from fastapi import APIRouter, HTTPException
import json
import logging
import httpx

from app.services.db import execute, query_one, query_all
from app.config import NACOS_HOST, NACOS_PORT, NACOS_NAMESPACE, NACOS_USERNAME, NACOS_PASSWORD

logger = logging.getLogger(__name__)
router = APIRouter()

TABLE = "agent"


@router.get("/agents")
async def list_agents():
    """List all agents"""
    rows = await query_all(
        f"SELECT a.*, m.name as model_name, m.provider as model_provider, m.model_id as model_model_id "
        f"FROM `{TABLE}` a LEFT JOIN ai_model m ON a.model_id = m.id "
        f"ORDER BY a.created_at DESC"
    )
    for row in rows:
        if isinstance(row.get("config"), str):
            try:
                row["config"] = json.loads(row["config"])
            except Exception:
                row["config"] = {}
        row["enabled"] = bool(row.get("enabled"))
    return {"code": 200, "data": rows}


@router.post("/agents")
async def create_agent(body: dict):
    name = (body.get("name") or "").strip()
    agent_type = (body.get("type") or "").strip()
    mcp_url = (body.get("mcp_url") or "").strip()
    model_id = body.get("model_id")
    description = (body.get("description") or "").strip()
    config = body.get("config") or {}
    if not name:
        raise HTTPException(status_code=400, detail="Name is required")
    if not agent_type:
        raise HTTPException(status_code=400, detail="Type is required")
    await execute(
        f"INSERT INTO `{TABLE}` (name, type, mcp_url, model_id, description, config, enabled, status, created_at, updated_at) "
        f"VALUES (%s, %s, %s, %s, %s, %s, %s, 'offline', UTC_TIMESTAMP(), UTC_TIMESTAMP())",
        (name, agent_type, mcp_url, model_id, description, json.dumps(config), int(body.get("enabled", True))),
    )
    row = await query_one(f"SELECT * FROM `{TABLE}` ORDER BY id DESC LIMIT 1")
    if row:
        row["enabled"] = bool(row.get("enabled"))
    return {"code": 200, "data": row, "message": "Agent created"}


@router.get("/agents/discover")
async def discover_services():
    services = []
    nacos_url = f"http://{NACOS_HOST}:{NACOS_PORT}/nacos/v1"
    try:
        async with httpx.AsyncClient(timeout=10) as client:
            resp = await client.get(f"{nacos_url}/ns/service/list", params={
                "pageNo": 1, "pageSize": 100, "namespaceId": NACOS_NAMESPACE,
                "username": NACOS_USERNAME, "password": NACOS_PASSWORD,
            })
            if resp.status_code == 200:
                data = resp.json()
                for svc in data.get("doms", []):
                    if svc.endswith("-mcp"):
                        try:
                            inst = await client.get(f"{nacos_url}/ns/instance/list", params={
                                "serviceName": svc, "namespaceId": NACOS_NAMESPACE,
                                "username": NACOS_USERNAME, "password": NACOS_PASSWORD,
                            })
                            if inst.status_code == 200:
                                hosts = inst.json().get("hosts", [])
                                if hosts:
                                    h = hosts[0]
                                    services.append({
                                        "name": svc, "url": f"http://{h['ip']}:{h['port']}/sse",
                                        "ip": h["ip"], "port": h["port"], "healthy": h.get("healthy", False),
                                    })
                        except Exception:
                            services.append({"name": svc, "url": "", "ip": "", "port": 0, "healthy": False})
    except Exception as e:
        logger.error(f"Nacos discover failed: {e}")
    return {"code": 200, "data": services}


@router.get("/agents/{agent_id}")
async def get_agent(agent_id: int):
    row = await query_one(f"SELECT * FROM `{TABLE}` WHERE id = %s", (agent_id,))
    if not row:
        raise HTTPException(status_code=404, detail="Agent not found")
    if isinstance(row.get("config"), str):
        try:
            row["config"] = json.loads(row["config"])
        except Exception:
            row["config"] = {}
    row["enabled"] = bool(row.get("enabled"))
    return {"code": 200, "data": row}


@router.put("/agents/{agent_id}")
async def update_agent(agent_id: int, body: dict):
    row = await query_one(f"SELECT * FROM `{TABLE}` WHERE id = %s", (agent_id,))
    if not row:
        raise HTTPException(status_code=404, detail="Agent not found")
    name = (body.get("name") or row.get("name", "")).strip()
    agent_type = (body.get("type") or row.get("type", "")).strip()
    mcp_url = (body.get("mcp_url") or row.get("mcp_url", "")).strip()
    model_id = body.get("model_id", row.get("model_id"))
    description = (body.get("description") or row.get("description", "")).strip()
    config = body.get("config") if "config" in body else row.get("config", {})
    enabled = int(body.get("enabled", row.get("enabled", True)))
    if isinstance(config, str):
        try:
            config = json.loads(config)
        except Exception:
            config = {}
    await execute(
        f"UPDATE `{TABLE}` SET name=%s, type=%s, mcp_url=%s, model_id=%s, description=%s, config=%s, enabled=%s, updated_at=UTC_TIMESTAMP() WHERE id=%s",
        (name, agent_type, mcp_url, model_id, description, json.dumps(config), enabled, agent_id),
    )
    updated = await query_one(f"SELECT * FROM `{TABLE}` WHERE id = %s", (agent_id,))
    if updated:
        updated["enabled"] = bool(updated.get("enabled"))
    return {"code": 200, "data": updated, "message": "Agent updated"}


@router.delete("/agents/{agent_id}")
async def delete_agent(agent_id: int):
    count = await execute(f"DELETE FROM `{TABLE}` WHERE id = %s", (agent_id,))
    if count == 0:
        raise HTTPException(status_code=404, detail="Agent not found")
    return {"code": 200, "message": "Agent deleted"}


@router.patch("/agents/{agent_id}")
async def patch_agent(agent_id: int, body: dict):
    return await update_agent(agent_id, body)


@router.get("/agents/{agent_id}/status")
async def check_agent_status(agent_id: int):
    row = await query_one(f"SELECT * FROM `{TABLE}` WHERE id = %s", (agent_id,))
    if not row:
        raise HTTPException(status_code=404, detail="Agent not found")
    agent_type = row.get("type", "")
    worker_service = f"agent-{agent_type}"
    status = "offline"
    try:
        async with httpx.AsyncClient(timeout=5) as client:
            resp = await client.get(f"http://{NACOS_HOST}:{NACOS_PORT}/nacos/v1/ns/instance/list", params={
                "serviceName": worker_service, "namespaceId": NACOS_NAMESPACE,
                "username": NACOS_USERNAME, "password": NACOS_PASSWORD,
            })
            if resp.status_code == 200:
                hosts = resp.json().get("hosts", [])
                if hosts and hosts[0].get("healthy"):
                    status = "online"
    except Exception:
        pass
    await execute(f"UPDATE `{TABLE}` SET status=%s, last_active_at=UTC_TIMESTAMP() WHERE id=%s", (status, agent_id))
    return {"code": 200, "data": {"status": status}}


# ==================== LLM & Tools ====================

def _build_tools_for_type(agent_type: str) -> list:
    if agent_type == "k8s":
        return [
            {"type": "function", "function": {"name": "k8s_list_pods", "description": "List Kubernetes pods", "parameters": {"type": "object", "properties": {"namespace": {"type": "string"}}, "required": []}}},
            {"type": "function", "function": {"name": "k8s_get_pod", "description": "Get pod details", "parameters": {"type": "object", "properties": {"name": {"type": "string"}, "namespace": {"type": "string"}}, "required": ["name"]}}},
            {"type": "function", "function": {"name": "k8s_pod_logs", "description": "Get pod logs", "parameters": {"type": "object", "properties": {"name": {"type": "string"}, "namespace": {"type": "string"}, "tail_lines": {"type": "integer"}, "container": {"type": "string"}}, "required": ["name"]}}},
            {"type": "function", "function": {"name": "k8s_list_deployments", "description": "List deployments", "parameters": {"type": "object", "properties": {"namespace": {"type": "string"}}, "required": []}}},
            {"type": "function", "function": {"name": "k8s_list_services", "description": "List services", "parameters": {"type": "object", "properties": {"namespace": {"type": "string"}}, "required": []}}},
            {"type": "function", "function": {"name": "k8s_list_nodes", "description": "List cluster nodes", "parameters": {"type": "object", "properties": {}, "required": []}}},
            {"type": "function", "function": {"name": "k8s_list_namespaces", "description": "List namespaces", "parameters": {"type": "object", "properties": {}, "required": []}}},
            {"type": "function", "function": {"name": "k8s_list_events", "description": "List recent events", "parameters": {"type": "object", "properties": {"namespace": {"type": "string"}, "limit": {"type": "integer"}}, "required": []}}},
            {"type": "function", "function": {"name": "k8s_scale_deployment", "description": "Scale a deployment", "parameters": {"type": "object", "properties": {"name": {"type": "string"}, "replicas": {"type": "integer"}, "namespace": {"type": "string"}}, "required": ["name", "replicas"]}}},
            {"type": "function", "function": {"name": "k8s_delete_pod", "description": "Delete a pod", "parameters": {"type": "object", "properties": {"name": {"type": "string"}, "namespace": {"type": "string"}}, "required": ["name"]}}},
            {"type": "function", "function": {"name": "k8s_pod_restart_reason", "description": "Analyze why a pod restarted", "parameters": {"type": "object", "properties": {"name": {"type": "string"}, "namespace": {"type": "string"}}, "required": ["name"]}}},
            {"type": "function", "function": {"name": "k8s_cluster_summary", "description": "Get cluster resource summary", "parameters": {"type": "object", "properties": {}, "required": []}}},
        ]
    elif agent_type == "weather":
        return [
            {"type": "function", "function": {"name": "get_current_weather", "description": "Get current weather", "parameters": {"type": "object", "properties": {"city": {"type": "string"}, "lang": {"type": "string"}}, "required": ["city"]}}},
            {"type": "function", "function": {"name": "get_weather_forecast", "description": "Get weather forecast", "parameters": {"type": "object", "properties": {"city": {"type": "string"}, "days": {"type": "integer"}, "lang": {"type": "string"}}, "required": ["city"]}}},
        ]
    return []


async def _call_mcp_tool(worker_url: str, tool_name: str, arguments: dict) -> str:
    try:
        async with httpx.AsyncClient(timeout=60) as client:
            resp = await client.post(f"{worker_url}/chat", json={"message": f"[tool:{tool_name}] {json.dumps(arguments)}"})
            if resp.status_code == 200:
                data = resp.json()
                return data.get("data", {}).get("response", str(data))
            return f"Error: {resp.status_code}"
    except Exception as e:
        return f"Error calling tool: {e}"


async def _call_llm(model_config: dict, messages: list, tools: list = None) -> dict:
    base_url = model_config.get("base_url", "").rstrip("/")
    api_key = model_config.get("api_key", "")
    model_name = model_config.get("model_id", "")

    headers = {"Content-Type": "application/json"}
    if api_key:
        headers["Authorization"] = f"Bearer {api_key}"

    # Extract system message for Ollama compatibility
    system_content = None
    clean_messages = []
    for m in messages:
        if m.get("role") == "system":
            system_content = m["content"]
        else:
            clean_msg = {"role": m["role"], "content": m.get("content", "")}
            if m.get("tool_calls"):
                clean_msg["tool_calls"] = m["tool_calls"]
            if m.get("tool_call_id"):
                clean_msg["tool_call_id"] = m["tool_call_id"]
            clean_messages.append(clean_msg)

    payload = {"model": model_name, "messages": clean_messages, "temperature": 0.3, "max_tokens": 2000}
    if system_content:
        payload["system"] = system_content
    if tools:
        payload["tools"] = tools

    logger.info(f"🤖 LLM request: model={model_name}, msgs={len(clean_messages)}, tools={len(tools) if tools else 0}")
    try:
        async with httpx.AsyncClient(timeout=30) as client:
            resp = await client.post(f"{base_url}/chat/completions", headers=headers, json=payload)
            if resp.status_code == 200:
                result = resp.json()
                content_preview = ""
                choices = result.get("choices", [])
                if choices:
                    c = choices[0].get("message", {})
                    content_preview = c.get("content", "")[:100]
                logger.info(f"🤖 LLM response: 200, finish={choices[0].get('finish_reason', '?')}, content='{content_preview}'")
                return result
            logger.error(f"🤖 LLM error: {resp.status_code} {resp.text[:500]}")
            return {"error": f"LLM API error: {resp.status_code}", "detail": resp.text[:500]}
    except httpx.TimeoutException:
        logger.error(f"🤖 LLM timeout after 30s")
        return {"error": "LLM timeout"}
    except Exception as e:
        logger.error(f"🤖 LLM call error: {type(e).__name__}: {e}")
        return {"error": f"{type(e).__name__}: {e}"}


@router.post("/agents/{agent_id}/chat")
async def chat_with_agent(agent_id: int, body: dict):
    message = body.get("message", "").strip()
    session_id = body.get("session_id", "default")
    if not message:
        raise HTTPException(status_code=400, detail="Message is required")
    logger.info(f"💬 [1/8] Chat: agent={agent_id}, msg='{message[:50]}'")

    agent = await query_one(f"SELECT * FROM `{TABLE}` WHERE id = %s", (agent_id,))
    if not agent:
        raise HTTPException(status_code=404, detail="Agent not found")

    agent_type = agent.get("type", "")
    model_id = agent.get("model_id")
    logger.info(f"💬 [2/8] Agent: type={agent_type}, model_id={model_id}")

    # Discover worker
    worker_service = f"agent-{agent_type}"
    worker_url = ""
    try:
        async with httpx.AsyncClient(timeout=5) as client:
            resp = await client.get(f"http://{NACOS_HOST}:{NACOS_PORT}/nacos/v1/ns/instance/list", params={
                "serviceName": worker_service, "namespaceId": NACOS_NAMESPACE,
                "username": NACOS_USERNAME, "password": NACOS_PASSWORD,
            })
            if resp.status_code == 200:
                hosts = resp.json().get("hosts", [])
                if hosts:
                    h = hosts[0]
                    worker_url = f"http://{h['ip']}:{h['port']}"
    except Exception:
        pass
    logger.info(f"💬 [3/8] Worker: {worker_service} -> {worker_url}")

    if not worker_url:
        raise HTTPException(status_code=502, detail=f"Agent worker '{worker_service}' not found")

    # No model -> keyword mode
    if not model_id:
        logger.info(f"💬 [4/8] Mode: keyword (no model)")
        try:
            async with httpx.AsyncClient(timeout=60) as client:
                resp = await client.post(f"{worker_url}/chat", json={"message": message, "session_id": session_id})
                if resp.status_code == 200:
                    await execute(f"UPDATE `{TABLE}` SET last_active_at=UTC_TIMESTAMP() WHERE id=%s", (agent_id,))
                    return resp.json()
                raise HTTPException(status_code=resp.status_code, detail=resp.text)
        except httpx.ConnectError:
            raise HTTPException(status_code=502, detail=f"Cannot connect to {worker_service}")
        except Exception as e:
            raise HTTPException(status_code=502, detail=str(e))

    # LLM mode
    model_config = await query_one("SELECT * FROM ai_model WHERE id = %s AND enabled = 1", (model_id,))
    if not model_config:
        raise HTTPException(status_code=400, detail="Model not found or disabled")
    logger.info(f"💬 [4/8] Mode: LLM ({model_config.get('name')}/{model_config.get('model_id')})")

    tools = _build_tools_for_type(agent_type)
    logger.info(f"💬 [5/8] Tools: {len(tools)} defined")

    system_prompt = (
        f"You are a helpful {agent_type} assistant. You have access to tools to manage {agent_type} resources. "
        "Always use the appropriate tool to answer user questions. "
        "When showing results, format them in a clean, readable way. "
        "For lists, use a table-like format. For single items, show key details clearly."
    )
    messages = [
        {"role": "system", "content": system_prompt},
        {"role": "user", "content": message},
    ]

    use_tools = bool(tools)
    for step in range(3):
        logger.info(f"💬 [6/8] LLM call #{step+1}, use_tools={use_tools}")
        llm_result = await _call_llm(model_config, messages, tools=tools if use_tools else None)

        if "error" in llm_result:
            if use_tools and "400" in str(llm_result.get("error", "")):
                logger.warning(f"💬 [6/8] 400 with tools -> retry without tools")
                use_tools = False
                llm_result = await _call_llm(model_config, messages)
                logger.info(f"💬 [6/8] Retry result keys: {list(llm_result.keys())}")
                if "error" in llm_result:
                    logger.error(f"💬 [6/8] Retry failed: {llm_result['error']}")
                    raise HTTPException(status_code=502, detail=llm_result["error"])
                choice = (llm_result.get("choices") or [{}])[0]
                content = choice.get("message", {}).get("content", "")
                logger.info(f"💬 [8/8] Retry OK, response: {len(content)} chars")
                if content:
                    await execute(f"UPDATE `{TABLE}` SET last_active_at=UTC_TIMESTAMP() WHERE id=%s", (agent_id,))
                    return {"code": 200, "data": {"response": content, "tool": "llm"}}
                return {"code": 200, "data": {"response": "No response from LLM", "tool": "llm"}}
            else:
                logger.error(f"💬 [6/8] LLM error: {llm_result}")
                raise HTTPException(status_code=502, detail=llm_result["error"])

        choice = (llm_result.get("choices") or [{}])[0]
        msg = choice.get("message", {})
        finish_reason = choice.get("finish_reason", "")
        logger.info(f"💬 [6/8] finish_reason={finish_reason}, content='{msg.get('content', '')[:80]}'")

        if finish_reason == "tool_calls" and msg.get("tool_calls"):
            messages.append(msg)
            for tc in msg["tool_calls"]:
                fn = tc.get("function", {})
                tool_name = fn.get("name", "")
                try:
                    tool_args = json.loads(fn.get("arguments", "{}"))
                except json.JSONDecodeError:
                    tool_args = {}
                logger.info(f"💬 [7/8] Calling tool: {tool_name}({tool_args})")
                tool_result = await _call_mcp_tool(worker_url, tool_name, tool_args)
                logger.info(f"💬 [7/8] Tool result: {len(tool_result)} chars")
                messages.append({"role": "tool", "tool_call_id": tc.get("id", ""), "content": tool_result})
            continue

        content = msg.get("content", "")
        logger.info(f"💬 [8/8] Final response: {len(content)} chars")
        if content:
            await execute(f"UPDATE `{TABLE}` SET last_active_at=UTC_TIMESTAMP() WHERE id=%s", (agent_id,))
            return {"code": 200, "data": {"response": content, "tool": "llm"}}

    return {"code": 200, "data": {"response": "Max tool calls reached", "tool": "llm"}}


TYPE_MCP_MAP = {
    "weather": "weather-mcp",
    "devops": "devops-mcp",
}
