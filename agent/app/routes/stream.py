"""
SSE streaming endpoint for agent chat
"""
import json
import logging
import os
import httpx

from fastapi import APIRouter, HTTPException, Request
from fastapi.responses import StreamingResponse

from app.services.db import execute, query_one
from app.config import NACOS_HOST, NACOS_PORT, NACOS_NAMESPACE, NACOS_USERNAME, NACOS_PASSWORD

logger = logging.getLogger(__name__)
router = APIRouter()

TABLE = "agent"


async def _discover_mcp_tools(agent_type: str) -> list:
    """Discover tools from MCP server via Nacos"""
    try:
        service_name = f"tool-{agent_type}"
        async with httpx.AsyncClient(timeout=5) as client:
            resp = await client.get(
                f"http://{NACOS_HOST}:{NACOS_PORT}/nacos/v1/ns/instance/list",
                params={"serviceName": service_name, "namespaceId": NACOS_NAMESPACE, "username": NACOS_USERNAME, "password": NACOS_PASSWORD},
            )
            if resp.status_code != 200:
                return []
            hosts = resp.json().get("hosts", [])
            if not hosts:
                return []
            mcp_url = f"http://{hosts[0]['ip']}:{hosts[0]['port']}"

        # List tools from MCP server
        from mcp.client.sse import sse_client
        from mcp import ClientSession
        async with sse_client(mcp_url + "/sse") as (read, write):
            async with ClientSession(read, write) as session:
                await session.initialize()
                tools_result = await session.list_tools()
                openai_tools = []
                for t in tools_result.tools:
                    openai_tools.append({
                        "type": "function",
                        "function": {
                            "name": t.name,
                            "description": t.description or t.name,
                            "parameters": t.inputSchema if t.inputSchema else {"type": "object", "properties": {}, "required": []},
                        }
                    })
                logger.info(f"Discovered {len(openai_tools)} tools from {service_name}")
                return openai_tools
    except Exception as e:
        logger.warning(f"MCP tool discovery failed for {agent_type}: {e}")
        return []


def _sse(event_type, data=None):
    """Format SSE event"""
    payload = {"type": event_type}
    if isinstance(data, dict):
        payload.update(data)
    elif isinstance(data, str):
        payload["text"] = data
    return "data: " + json.dumps(payload, ensure_ascii=False) + "\n\n"


async def _call_mcp_tool(worker_url, tool_name, arguments):
    try:
        async with httpx.AsyncClient(timeout=60) as client:
            resp = await client.post(worker_url + "/chat", json={"message": "[tool:" + tool_name + "] " + json.dumps(arguments)})
            if resp.status_code == 200:
                data = resp.json()
                return data.get("data", {}).get("response", str(data))
            return "Error: " + str(resp.status_code)
    except Exception as e:
        return "Error calling tool: " + str(e)


async def _call_llm_stream(model_config, messages, tools=None):
    """Streaming LLM call, yields (event_type, data) tuples"""
    base_url = model_config.get("base_url", "").rstrip("/")
    api_key = model_config.get("api_key", "")
    model_name = model_config.get("model_id", "")

    headers = {"Content-Type": "application/json"}
    if api_key:
        headers["Authorization"] = "Bearer " + api_key

    clean_messages = []
    for m in messages:
        clean_msg = {"role": m["role"], "content": m.get("content", "")}
        if m.get("tool_calls"):
            clean_msg["tool_calls"] = m["tool_calls"]
        if m.get("tool_call_id"):
            clean_msg["tool_call_id"] = m["tool_call_id"]
        clean_messages.append(clean_msg)

    payload = {"model": model_name, "messages": clean_messages, "temperature": 0.3, "max_tokens": 4000, "stream": True}
    if tools:
        payload["tools"] = tools

    try:
        async with httpx.AsyncClient(timeout=120) as client:
            async with client.stream("POST", base_url + "/chat/completions", headers=headers, json=payload) as resp:
                if resp.status_code != 200:
                    error_text = await resp.aread()
                    yield ("error", error_text.decode()[:300])
                    return

                finish_reason = None
                content_buffer = ""
                tool_calls = []

                async for line in resp.aiter_lines():
                    if not line.startswith("data: "):
                        continue
                    data_str = line[6:]
                    if data_str.strip() == "[DONE]":
                        break
                    try:
                        chunk = json.loads(data_str)
                    except json.JSONDecodeError:
                        continue

                    choices = chunk.get("choices") or []
                    if not choices:
                        continue
                    delta = choices[0].get("delta") or {}
                    fr = choices[0].get("finish_reason")
                    if fr:
                        finish_reason = fr

                    if delta.get("content"):
                        content_buffer += delta["content"]
                        yield ("content", delta["content"])

                    if delta.get("tool_calls"):
                        for tc_delta in (delta.get("tool_calls") or []):
                            idx = tc_delta.get("index", 0)
                            while len(tool_calls) <= idx:
                                tool_calls.append({"id": "", "type": "function", "function": {"name": "", "arguments": ""}})
                            tc = tool_calls[idx]
                            if tc_delta.get("id"):
                                tc["id"] = tc_delta["id"]
                            if tc_delta.get("function"):
                                fn = tc_delta["function"]
                                if fn.get("name"):
                                    tc["function"]["name"] = fn["name"]
                                if fn.get("arguments"):
                                    tc["function"]["arguments"] += fn["arguments"]

                yield ("done", {"finish_reason": finish_reason, "content": content_buffer, "tool_calls": tool_calls or None})

    except httpx.TimeoutException:
        yield ("error", "LLM timeout after 120s")
    except Exception as e:
        yield ("error", str(type(e).__name__) + ": " + str(e))


async def _call_llm_nonstream(model_config, messages, tools=None):
    """Non-streaming fallback"""
    base_url = model_config.get("base_url", "").rstrip("/")
    api_key = model_config.get("api_key", "")
    model_name = model_config.get("model_id", "")

    headers = {"Content-Type": "application/json"}
    if api_key:
        headers["Authorization"] = "Bearer " + api_key

    clean_messages = []
    for m in messages:
        clean_messages.append({"role": m["role"], "content": m.get("content", "")})

    payload = {"model": model_name, "messages": clean_messages, "temperature": 0.3, "max_tokens": 4000}
    if tools:
        payload["tools"] = tools

    try:
        async with httpx.AsyncClient(timeout=120) as client:
            resp = await client.post(base_url + "/chat/completions", headers=headers, json=payload)
            if resp.status_code == 200:
                return resp.json()
            return {"error": "LLM API error: " + str(resp.status_code)}
    except Exception as e:
        return {"error": str(type(e).__name__) + ": " + str(e)}


async def _build_system_prompt(agent_id: int, agent_type: str, agent_name: str) -> str:
    """Build system prompt from agent's MD files in MongoDB"""
    import motor.motor_asyncio
    parts = [f"You are {agent_name}, a {agent_type} management assistant."]
    try:
        mongo_url = f"mongodb://{os.getenv('MONGODB_USERNAME','root')}:{os.getenv('MONGODB_PASSWORD','root123')}@{os.getenv('MONGODB_HOST','192.168.86.9')}:{os.getenv('MONGODB_PORT','27017')}/{os.getenv('MONGODB_DATABASE','agent')}?authSource={os.getenv('MONGODB_AUTH_DB','admin')}"
        logger.info(f"_build_system_prompt: Connecting to MongoDB for agent {agent_id}")
        client = motor.motor_asyncio.AsyncIOMotorClient(mongo_url, serverSelectionTimeoutMS=5000)
        db = client[os.getenv('MONGODB_DATABASE', 'agent')]
        doc = await db.agent_tools.find_one({"agent_id": agent_id}, {"_id": 0})
        logger.info(f"_build_system_prompt: MongoDB doc found: {bool(doc)}, keys: {list(doc.keys()) if doc else 'None'}")
        if doc:
            files = doc.get("files", [])
            logger.info(f"_build_system_prompt: files count: {len(files)}")
            if not files:
                # Fallback: build from old tools/prompts format
                tools = doc.get("tools", [])
                prompts = doc.get("prompts", [])
                if tools or prompts:
                    tool_desc = []
                    for t in tools:
                        p = ', '.join([x.get('name','') for x in t.get('params',[])])
                        tool_desc.append(f"- {t.get('name','')}: {t.get('description','')} (command: {t.get('command','')}, params: {p})")
                    for p in prompts:
                        tool_desc.append(f"- {p.get('name','')}: {p.get('description','')} (command: {p.get('command','')})")
                    if tool_desc:
                        parts.append("\nAvailable tools:\n" + '\n'.join(tool_desc))
            else:
                priority = ['AGENTS.md', 'SOUL.md', 'TOOLS.md', 'IDENTITY.md', 'USER.md', 'MEMORY.md']
                files = sorted(files, key=lambda f: (priority.index(f['name']) if f['name'] in priority else 99, f['name']))
                for f in files:
                    content = (f.get('content') or '').strip()
                    if content:
                        parts.append(f"\n--- {f['name']} ---\n{content}")
                        logger.info(f"_build_system_prompt: Added {f['name']} ({len(content)} chars)")
        logger.info(f"System prompt built for agent {agent_id}: {len(parts)} sections, total {len('\n'.join(parts))} chars")
    except Exception as e:
        logger.error(f"Build system prompt error: {e}", exc_info=True)
    parts.append("\nCRITICAL: When presenting tool results, show the EXACT data returned by the tool. DO NOT rename, translate, or fabricate any fields. Show account names, zone names, IDs exactly as returned. If a name is empty or looks like a raw ID, show it as-is.")
    parts.append("\nAlways use the appropriate tool when available. Format results cleanly. Respond in the user's language.")
    return "\n".join(parts)


async def _discover_worker(agent_type):
    worker_service = "agent-" + agent_type
    try:
        async with httpx.AsyncClient(timeout=5) as client:
            resp = await client.get(
                "http://" + NACOS_HOST + ":" + str(NACOS_PORT) + "/nacos/v1/ns/instance/list",
                params={"serviceName": worker_service, "namespaceId": NACOS_NAMESPACE, "username": NACOS_USERNAME, "password": NACOS_PASSWORD},
            )
            if resp.status_code == 200:
                hosts = resp.json().get("hosts", [])
                if hosts:
                    h = hosts[0]
                    return "http://" + h["ip"] + ":" + str(h["port"])
    except Exception:
        pass
    return ""


@router.get("/agents/{agent_id}/chat/stream")
async def chat_stream(agent_id: int, message: str, session_id: str = "default"):
    """SSE streaming chat endpoint"""
    async def event_generator():
        try:
            yield _sse("status", "Starting...")

            agent = await query_one("SELECT * FROM `" + TABLE + "` WHERE id = %s", (agent_id,))
            if not agent:
                yield _sse("error", "Agent not found")
                return

            agent_type = agent.get("type", "")
            model_id = agent.get("model_id")
            worker_url = await _discover_worker(agent_type)

            if not worker_url:
                yield _sse("error", "Worker not found: " + agent_type)
                return

            # No model -> keyword mode
            if not model_id:
                yield _sse("status", "Processing...")
                try:
                    async with httpx.AsyncClient(timeout=60) as client:
                        resp = await client.post(worker_url + "/chat", json={"message": message, "session_id": session_id})
                        if resp.status_code == 200:
                            data = resp.json()
                            text = data.get("data", {}).get("response", str(data))
                            yield _sse("response", text)
                        else:
                            yield _sse("error", resp.text[:200])
                except Exception as e:
                    yield _sse("error", str(e))
                await execute("UPDATE `" + TABLE + "` SET last_active_at=UTC_TIMESTAMP() WHERE id=%s", (agent_id,))
                return

            # LLM mode
            model_config = await query_one("SELECT * FROM ai_model WHERE id = %s AND enabled = 1", (model_id,))
            if not model_config:
                yield _sse("error", "Model not found or disabled")
                return

            tools = await _discover_mcp_tools(agent_type)
            system_prompt = await _build_system_prompt(agent_id, agent_type, agent.get("name", "Agent"))

            # Load chat history from Redis
            import redis.asyncio as aioredis
            history_messages = []
            redis_conn = None
            try:
                redis_conn = aioredis.Redis(
                    host=os.getenv("REDIS_HOST", "192.168.86.9"),
                    port=int(os.getenv("REDIS_PORT", "6379")),
                    password=os.getenv("REDIS_PASSWORD", "root123") or None,
                    db=int(os.getenv("REDIS_DATABASE", "0")),
                    decode_responses=True,
                )
                prefix_map = {"weather": "weather", "cloudflare": "cf", "k8s": "k8s"}
                prefix = prefix_map.get(agent_type, agent_type)
                chat_key = f"agent:{prefix}:chat:{session_id}"
                raw_history = await redis_conn.lrange(chat_key, 0, -1)
                # Parse history (skip last user message if it's the current one)
                for item in reversed(raw_history):
                    try:
                        msg = json.loads(item)
                        if msg.get("role") == "user" and msg.get("text", msg.get("content", "")) == message:
                            continue  # Skip current message
                        history_messages.insert(0, {"role": msg["role"], "content": msg.get("text", msg.get("content", ""))})
                    except (json.JSONDecodeError, KeyError):
                        continue
                history_messages = history_messages[-20:]
                # Save user message to Redis
                import time as _time
                entry = {"session_id": session_id, "role": "user", "text": message, "ts": int(_time.time())}
                await redis_conn.rpush(chat_key, json.dumps(entry, ensure_ascii=False))
                await redis_conn.expire(chat_key, 86400)
            except Exception as e:
                logger.warning(f"Load/save chat history error: {e}")

            messages = [
                {"role": "system", "content": system_prompt},
                *history_messages,
                {"role": "user", "content": message},
            ]

            yield _sse("status", "Using model: " + model_config.get("name", ""))

            use_tools = bool(tools)
            for step in range(3):
                yield _sse("thinking", None)

                if use_tools:
                    finish_reason = None
                    content_buffer = ""
                    tool_calls = []
                    stream_ok = False

                    async for event_type, event_data in _call_llm_stream(model_config, messages, tools=tools):
                        if event_type == "content":
                            yield _sse("content", event_data)
                            stream_ok = True
                        elif event_type == "done":
                            finish_reason = event_data["finish_reason"]
                            content_buffer = event_data["content"]
                            tool_calls = event_data.get("tool_calls") or []
                        elif event_type == "error":
                            # Fallback to non-streaming
                            yield _sse("status", "Retrying...")
                            result = await _call_llm_nonstream(model_config, messages)
                            if "error" in result:
                                yield _sse("error", result["error"])
                                return
                            choice = (result.get("choices") or [{}])[0]
                            finish_reason = choice.get("finish_reason", "")
                            content_buffer = choice.get("message", {}).get("content", "")
                            if content_buffer:
                                yield _sse("response", content_buffer)
                            await execute("UPDATE `" + TABLE + "` SET last_active_at=UTC_TIMESTAMP() WHERE id=%s", (agent_id,))
                            return

                    if finish_reason == "tool_calls" and tool_calls:
                        messages.append({"role": "assistant", "content": content_buffer or None, "tool_calls": tool_calls})
                        for tc in tool_calls:
                            fn = tc.get("function", {})
                            tool_name = fn.get("name", "")
                            try:
                                tool_args = json.loads(fn.get("arguments", "{}"))
                            except json.JSONDecodeError:
                                tool_args = {}
                            yield _sse("status", "Calling " + tool_name + "...")
                            tool_result = await _call_mcp_tool(worker_url, tool_name, tool_args)
                            messages.append({"role": "tool", "tool_call_id": tc.get("id", ""), "content": tool_result})
                        continue
                    elif content_buffer:
                        # Save assistant response to Redis
                        if redis_conn and chat_key:
                            try:
                                import time as _time
                                entry = {"session_id": session_id, "role": "assistant", "text": content_buffer, "ts": int(_time.time())}
                                await redis_conn.rpush(chat_key, json.dumps(entry, ensure_ascii=False))
                                await redis_conn.expire(chat_key, 86400)
                            except Exception:
                                pass
                        yield _sse("done", None)
                        await execute("UPDATE `" + TABLE + "` SET last_active_at=UTC_TIMESTAMP() WHERE id=%s", (agent_id,))
                        return
                    elif not stream_ok and not tool_calls:
                        # No streaming events at all, try non-streaming
                        yield _sse("status", "Retrying non-stream...")
                        result = await _call_llm_nonstream(model_config, messages)
                        if "error" in result:
                            yield _sse("error", result["error"])
                            return
                        choice = (result.get("choices") or [{}])[0]
                        content = choice.get("message", {}).get("content", "")
                        if content:
                            yield _sse("response", content)
                            # Save assistant response to Redis
                            if redis_conn and chat_key:
                                try:
                                    import time as _time
                                    entry = {"session_id": session_id, "role": "assistant", "text": content, "ts": int(_time.time())}
                                    await redis_conn.rpush(chat_key, json.dumps(entry, ensure_ascii=False))
                                    await redis_conn.expire(chat_key, 86400)
                                except Exception:
                                    pass
                        await execute("UPDATE `" + TABLE + "` SET last_active_at=UTC_TIMESTAMP() WHERE id=%s", (agent_id,))
                        return
                    else:
                        yield _sse("error", "Empty response")
                        return
                else:
                    # No tools
                    result = await _call_llm_nonstream(model_config, messages)
                    if "error" in result:
                        yield _sse("error", result["error"])
                        return
                    choice = (result.get("choices") or [{}])[0]
                    content = choice.get("message", {}).get("content", "")
                    if content:
                        yield _sse("response", content)
                        # Save assistant response to Redis
                        if redis_conn and chat_key:
                            try:
                                import time as _time
                                entry = {"session_id": session_id, "role": "assistant", "text": content, "ts": int(_time.time())}
                                await redis_conn.rpush(chat_key, json.dumps(entry, ensure_ascii=False))
                                await redis_conn.expire(chat_key, 86400)
                            except Exception:
                                pass
                    await execute("UPDATE `" + TABLE + "` SET last_active_at=UTC_TIMESTAMP() WHERE id=%s", (agent_id,))
                    return

            yield _sse("done", None)

        except Exception as e:
            import traceback
            logger.error("SSE error: " + str(type(e).__name__) + ": " + str(e))
            logger.error(traceback.format_exc())
            yield _sse("error", str(type(e).__name__) + ": " + str(e))

    return StreamingResponse(
        event_generator(),
        media_type="text/event-stream",
        headers={"Cache-Control": "no-cache", "Connection": "keep-alive", "X-Accel-Buffering": "no"},
    )
