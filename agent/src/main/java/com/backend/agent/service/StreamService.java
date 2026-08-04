package com.backend.agent.service;

import com.backend.agent.entity.AgentEntity;
import com.backend.agent.entity.ModelEntity;
import com.backend.agent.mapper.AgentMapper;
import com.backend.agent.mapper.ModelMapper;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URI;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

import static org.springframework.data.mongodb.core.query.Criteria.where;

/**
 * SSE streaming service for agent chat.
 * Matches agent-bak/app/routes/stream.py logic.
 *
 * SSE event format:
 *   data: {"type":"status","text":"..."}
 *   data: {"type":"content","text":"..."}
 *   data: {"type":"error","text":"..."}
 *   data: {"type":"thinking"}
 *   data: {"type":"done"}
 */
@Slf4j
@Service
public class StreamService {

    @Autowired
    private AgentMapper agentMapper;

    @Autowired
    private ModelMapper modelMapper;

    @Autowired
    private RestTemplate restTemplate;

    @Autowired(required = false)
    private StringRedisTemplate redisTemplate;

    @Autowired(required = false)
    private MongoTemplate mongoTemplate;

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final Map<String, CompletableFuture<Void>> activeStreams = new ConcurrentHashMap<>();

    @Value("${spring.cloud.nacos.discovery.server-addr:192.168.86.9:8848}")
    private String nacosServerAddr;

    @Value("${spring.cloud.nacos.discovery.namespace:6c5b1db3-a808-4543-a87e-6642e372cb4f}")
    private String nacosNamespace;

    /**
     * Main SSE streaming chat method.
     * Sends SSE events: status, thinking, content, response, error, done
     */
    public void streamChat(Integer agentId, String message, String sessionId, SseEmitter emitter) {
        var future = CompletableFuture.runAsync(() -> {
            try {
                sendSseEvent(emitter, "status", "Starting...");

                AgentEntity agent = agentMapper.selectById(agentId);
                if (agent == null) {
                    sendSseEvent(emitter, "error", "Agent not found");
                    emitter.complete();
                    return;
                }

                String agentType = agent.getType();
                Integer modelId = agent.getModelId();
                String workerUrl = discoverWorker(agentType);

                if (workerUrl.isEmpty()) {
                    sendSseEvent(emitter, "error", "Worker not found: " + agentType);
                    emitter.complete();
                    return;
                }

                // No model -> keyword mode
                if (modelId == null) {
                    sendSseEvent(emitter, "status", "Processing...");
                    try {
                        Map<String, String> request = new HashMap<>();
                        request.put("message", message);
                        request.put("session_id", sessionId);
                        HttpHeaders headers = new HttpHeaders();
                        headers.setContentType(MediaType.APPLICATION_JSON);
                        HttpEntity<Map<String, String>> entity = new HttpEntity<>(request, headers);
                        String resp = restTemplate.postForObject(workerUrl + "/chat", entity, String.class);

                        if (resp != null) {
                            Map<String, Object> data = objectMapper.readValue(resp, new TypeReference<Map<String, Object>>() {});
                            @SuppressWarnings("unchecked")
                            Map<String, Object> innerData = (Map<String, Object>) data.getOrDefault("data", new HashMap<>());
                            String text = (String) innerData.getOrDefault("response", resp);
                            sendSseEvent(emitter, "response", text);
                        } else {
                            sendSseEvent(emitter, "error", "Empty response from worker");
                        }
                    } catch (Exception e) {
                        sendSseEvent(emitter, "error", e.getMessage());
                    }
                    updateLastActive(agentId);
                    emitter.complete();
                    return;
                }

                // LLM mode
                ModelEntity modelConfig = modelMapper.selectById(modelId);
                if (modelConfig == null || !Boolean.TRUE.equals(modelConfig.getEnabled())) {
                    sendSseEvent(emitter, "error", "Model not found or disabled");
                    emitter.complete();
                    return;
                }

                // Discover MCP tools
                List<Map<String, Object>> tools = discoverMCPTools(agentType);

                // Build system prompt from MongoDB agent files
                String systemPrompt = buildSystemPrompt(agentId, agentType, agent.getName());
                sendSseEvent(emitter, "status", "Using model: " + modelConfig.getName());

                // Load chat history from Redis
                String prefix = getAgentPrefix(agentType);
                String chatKey = "agent:" + prefix + ":chat:" + sessionId;
                List<Map<String, Object>> historyMessages = loadChatHistory(chatKey, message);

                // Save current user message to Redis
                saveChatMessage(chatKey, sessionId, "user", message);

                List<Map<String, Object>> messages = new ArrayList<>();
                messages.add(Map.of("role", "system", "content", systemPrompt));
                messages.addAll(historyMessages);
                messages.add(Map.of("role", "user", "content", message));

                boolean useTools = !tools.isEmpty();
                for (int step = 0; step < 3; step++) {
                    sendSseEvent(emitter, "thinking", null);

                    if (useTools) {
                        // Streaming LLM call with tools
                        String collectedContent = streamLLM(emitter, modelConfig, messages, tools);

                        if (collectedContent == null) {
                            // Error occurred, already sent via SSE
                            return;
                        }

                        // After streaming, we need to check for tool calls
                        // For simplicity, do a non-streaming call to check tool_calls after
                        Map<String, Object> checkResult = callLLMNonStream(modelConfig, messages, tools);
                        if (checkResult.containsKey("error")) {
                            sendSseEvent(emitter, "error", (String) checkResult.get("error"));
                            return;
                        }

                        String finishReason = extractFinishReason(checkResult);
                        @SuppressWarnings("unchecked")
                        List<Map<String, Object>> toolCalls = extractToolCalls(checkResult);

                        if ("tool_calls".equals(finishReason) && toolCalls != null && !toolCalls.isEmpty()) {
                            Map<String, Object> assistantMsg = new HashMap<>();
                            assistantMsg.put("role", "assistant");
                            assistantMsg.put("content", collectedContent);
                            assistantMsg.put("tool_calls", toolCalls);
                            messages.add(assistantMsg);

                            for (Map<String, Object> tc : toolCalls) {
                                @SuppressWarnings("unchecked")
                                Map<String, Object> fn = (Map<String, Object>) tc.getOrDefault("function", new HashMap<>());
                                String toolName = (String) fn.getOrDefault("name", "");
                                String argsStr = (String) fn.getOrDefault("arguments", "{}");
                                Map<String, Object> toolArgs;
                                try {
                                    toolArgs = objectMapper.readValue(argsStr, new TypeReference<Map<String, Object>>() {});
                                } catch (Exception e) {
                                    toolArgs = new HashMap<>();
                                }
                                sendSseEvent(emitter, "status", "Calling " + toolName + "...");
                                String toolResult = callMCPTool(workerUrl, toolName, toolArgs);
                                Map<String, Object> toolMsg = new HashMap<>();
                                toolMsg.put("role", "tool");
                                toolMsg.put("tool_call_id", tc.getOrDefault("id", ""));
                                toolMsg.put("content", toolResult);
                                messages.add(toolMsg);
                            }
                            continue;
                        } else if (collectedContent != null && !collectedContent.isEmpty()) {
                            // Save assistant response to Redis
                            saveChatMessage(chatKey, sessionId, "assistant", collectedContent);
                            sendSseEvent(emitter, "done", null);
                            updateLastActive(agentId);
                            emitter.complete();
                            return;
                        }
                    } else {
                        // No tools — use non-streaming
                        Map<String, Object> result = callLLMNonStream(modelConfig, messages, null);
                        if (result.containsKey("error")) {
                            sendSseEvent(emitter, "error", (String) result.get("error"));
                            return;
                        }
                        String content = extractContent(result);
                        if (content != null && !content.isEmpty()) {
                            sendSseEvent(emitter, "response", content);
                            saveChatMessage(chatKey, sessionId, "assistant", content);
                        }
                        updateLastActive(agentId);
                        emitter.complete();
                        return;
                    }
                }

                sendSseEvent(emitter, "done", null);
                emitter.complete();

            } catch (Exception e) {
                log.error("SSE error: {}: {}", e.getClass().getSimpleName(), e.getMessage());
                try {
                    sendSseEvent(emitter, "error", e.getClass().getSimpleName() + ": " + e.getMessage());
                } catch (Exception ignored) {}
                try {
                    emitter.completeWithError(e);
                } catch (Exception ignored) {}
            } finally {
                activeStreams.remove(sessionId);
            }
        });

        activeStreams.put(sessionId, future);

        emitter.onCompletion(() -> activeStreams.remove(sessionId));
        emitter.onTimeout(() -> activeStreams.remove(sessionId));
        emitter.onError(e -> activeStreams.remove(sessionId));
    }

    /**
     * Stream LLM response to emitter. Returns collected full content or null on error.
     */
    private String streamLLM(SseEmitter emitter, ModelEntity modelConfig,
                             List<Map<String, Object>> messages, List<Map<String, Object>> tools) {
        String baseUrl = modelConfig.getBaseUrl();
        if (baseUrl != null && baseUrl.endsWith("/")) {
            baseUrl = baseUrl.substring(0, baseUrl.length() - 1);
        }
        String apiKey = modelConfig.getApiKey();
        String modelName = modelConfig.getModel();

        List<Map<String, Object>> cleanMessages = buildCleanMessages(messages);

        Map<String, Object> payload = new HashMap<>();
        payload.put("model", modelName);
        payload.put("messages", cleanMessages);
        payload.put("temperature", 0.3);
        payload.put("max_tokens", 4000);
        payload.put("stream", true);
        if (tools != null && !tools.isEmpty()) {
            payload.put("tools", tools);
        }

        try {
            URI uri = new URI(baseUrl + "/chat/completions");
            HttpURLConnection conn = (HttpURLConnection) uri.toURL().openConnection();
            conn.setRequestMethod("POST");
            conn.setRequestProperty("Content-Type", "application/json");
            conn.setRequestProperty("Accept", "text/event-stream");
            if (apiKey != null && !apiKey.isEmpty()) {
                conn.setRequestProperty("Authorization", "Bearer " + apiKey);
            }
            conn.setDoOutput(true);
            conn.setConnectTimeout(30000);
            conn.setReadTimeout(120000);

            String jsonBody = objectMapper.writeValueAsString(payload);
            try (var os = conn.getOutputStream()) {
                os.write(jsonBody.getBytes());
                os.flush();
            }

            int statusCode = conn.getResponseCode();
            if (statusCode != 200) {
                String errorMsg;
                try (var es = new BufferedReader(new InputStreamReader(conn.getErrorStream()))) {
                    StringBuilder sb = new StringBuilder();
                    String line;
                    while ((line = es.readLine()) != null) sb.append(line);
                    errorMsg = sb.toString();
                }
                sendSseEvent(emitter, "error", "LLM API error " + statusCode + ": " + errorMsg);
                return null;
            }

            StringBuilder contentBuffer = new StringBuilder();
            try (var br = new BufferedReader(new InputStreamReader(conn.getInputStream()))) {
                String line;
                while ((line = br.readLine()) != null) {
                    if (Thread.currentThread().isInterrupted()) break;
                    if (!line.startsWith("data: ")) continue;

                    String data = line.substring(6).trim();
                    if ("[DONE]".equals(data)) break;

                    try {
                        Map<String, Object> chunk = objectMapper.readValue(data, new TypeReference<Map<String, Object>>() {});
                        @SuppressWarnings("unchecked")
                        List<Map<String, Object>> choices = (List<Map<String, Object>>) chunk.getOrDefault("choices", List.of());
                        if (choices.isEmpty()) continue;

                        @SuppressWarnings("unchecked")
                        Map<String, Object> delta = (Map<String, Object>) choices.get(0).getOrDefault("delta", new HashMap<>());
                        String content = (String) delta.getOrDefault("content", "");
                        if (content != null && !content.isEmpty()) {
                            contentBuffer.append(content);
                            sendSseEvent(emitter, "content", content);
                        }
                    } catch (Exception e) {
                        log.warn("Failed to parse SSE chunk: {}", data);
                    }
                }
            }

            return contentBuffer.toString();

        } catch (Exception e) {
            log.error("Stream LLM error: {}", e.getMessage());
            sendSseEvent(emitter, "error", e.getClass().getSimpleName() + ": " + e.getMessage());
            return null;
        }
    }

    /**
     * Non-streaming LLM call fallback.
     */
    private Map<String, Object> callLLMNonStream(ModelEntity modelConfig,
                                                  List<Map<String, Object>> messages,
                                                  List<Map<String, Object>> tools) {
        String baseUrl = modelConfig.getBaseUrl();
        if (baseUrl != null && baseUrl.endsWith("/")) {
            baseUrl = baseUrl.substring(0, baseUrl.length() - 1);
        }
        String apiKey = modelConfig.getApiKey();
        String modelName = modelConfig.getModel();

        List<Map<String, Object>> cleanMessages = buildCleanMessages(messages);

        Map<String, Object> payload = new HashMap<>();
        payload.put("model", modelName);
        payload.put("messages", cleanMessages);
        payload.put("temperature", 0.3);
        payload.put("max_tokens", 4000);
        if (tools != null && !tools.isEmpty()) {
            payload.put("tools", tools);
        }

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        if (apiKey != null && !apiKey.isEmpty()) {
            headers.setBearerAuth(apiKey);
        }

        try {
            HttpEntity<Map<String, Object>> entity = new HttpEntity<>(payload, headers);
            String resp = restTemplate.postForObject(baseUrl + "/chat/completions", entity, String.class);
            if (resp != null) {
                return objectMapper.readValue(resp, new TypeReference<Map<String, Object>>() {});
            }
            return Map.of("error", "Empty LLM response");
        } catch (Exception e) {
            return Map.of("error", e.getClass().getSimpleName() + ": " + e.getMessage());
        }
    }

    /**
     * Discover MCP tools from Nacos (services named "tool-{type}").
     */
    private List<Map<String, Object>> discoverMCPTools(String agentType) {
        List<Map<String, Object>> tools = new ArrayList<>();
        String serviceName = "tool-" + agentType;
        String nacosUrl = buildNacosUrl();

        try {
            String resp = restTemplate.getForObject(
                    nacosUrl + "/ns/instance/list?serviceName={svc}&namespaceId={ns}&username={user}&password={pass}",
                    String.class,
                    serviceName, nacosNamespace, "", ""
            );
            if (resp == null) return tools;

            Map<String, Object> data = objectMapper.readValue(resp, new TypeReference<Map<String, Object>>() {});
            @SuppressWarnings("unchecked")
            List<Map<String, Object>> hosts = (List<Map<String, Object>>) data.getOrDefault("hosts", new ArrayList<>());
            if (hosts.isEmpty()) return tools;

            String mcpUrl = "http://" + hosts.get(0).get("ip") + ":" + hosts.get(0).get("port");
            log.info("Discovered MCP server: {} at {}", serviceName, mcpUrl);

            // Try to list tools from MCP SSE endpoint (simplified — just return known tools for now)
            // In a full implementation, we'd use MCP client SDK
            return buildBuiltinTools(agentType);
        } catch (Exception e) {
            log.warn("MCP tool discovery failed for {}: {}", agentType, e.getMessage());
            return buildBuiltinTools(agentType);
        }
    }

    private List<Map<String, Object>> buildBuiltinTools(String agentType) {
        if ("k8s".equals(agentType)) {
            return buildK8sTools();
        } else if ("weather".equals(agentType)) {
            return buildWeatherTools();
        }
        return new ArrayList<>();
    }

    /**
     * Build system prompt from agent's MD files in MongoDB.
     */
    private String buildSystemPrompt(Integer agentId, String agentType, String agentName) {
        List<String> parts = new ArrayList<>();
        parts.add("You are " + agentName + ", a " + agentType + " management assistant.");

        try {
            if (mongoTemplate != null) {
                Query query = Query.query(where("agent_id").is(agentId));
                Map<String, Object> doc = mongoTemplate.findOne(query, Map.class, "agent_tools");
                if (doc != null) {
                    @SuppressWarnings("unchecked")
                    List<Map<String, Object>> files = (List<Map<String, Object>>) doc.getOrDefault("files", new ArrayList<>());
                    if (!files.isEmpty()) {
                        List<String> priority = List.of("AGENTS.md", "SOUL.md", "TOOLS.md", "IDENTITY.md", "USER.md", "MEMORY.md");
                        files.sort((a, b) -> {
                            int ai = priority.indexOf(a.get("name"));
                            int bi = priority.indexOf(b.get("name"));
                            if (ai == -1) ai = 99;
                            if (bi == -1) bi = 99;
                            int cmp = Integer.compare(ai, bi);
                            if (cmp == 0) return ((String) a.get("name")).compareTo((String) b.get("name"));
                            return cmp;
                        });
                        for (Map<String, Object> f : files) {
                            String content = (String) f.getOrDefault("content", "");
                            if (!content.isBlank()) {
                                parts.add("\n--- " + f.get("name") + " ---\n" + content);
                            }
                        }
                    }
                }
            }
        } catch (Exception e) {
            log.warn("Build system prompt error: {}", e.getMessage());
        }

        parts.add("\nCRITICAL: When presenting tool results, show the EXACT data returned by the tool. " +
                "DO NOT rename, translate, or fabricate any fields. " +
                "Show account names, zone names, IDs exactly as returned.");
        parts.add("\nAlways use the appropriate tool when available. Format results cleanly. Respond in the user's language.");
        return String.join("\n", parts);
    }

    /**
     * Load chat history from Redis.
     */
    private List<Map<String, Object>> loadChatHistory(String chatKey, String currentMessage) {
        List<Map<String, Object>> history = new ArrayList<>();
        if (redisTemplate == null) return history;

        try {
            List<String> rawHistory = redisTemplate.opsForList().range(chatKey, 0, -1);
            if (rawHistory == null) return history;

            for (String item : rawHistory) {
                try {
                    Map<String, Object> msg = objectMapper.readValue(item, new TypeReference<Map<String, Object>>() {});
                    String role = (String) msg.getOrDefault("role", "");
                    String text = (String) msg.getOrDefault("text", msg.getOrDefault("content", ""));
                    if ("user".equals(role) && text.equals(currentMessage)) {
                        continue; // Skip current message
                    }
                    Map<String, Object> entry = new HashMap<>();
                    entry.put("role", role);
                    entry.put("content", text);
                    history.add(entry);
                } catch (Exception ignored) {}
            }
            // Keep last 20 messages
            if (history.size() > 20) {
                history = history.subList(history.size() - 20, history.size());
            }
        } catch (Exception e) {
            log.warn("Load chat history error: {}", e.getMessage());
        }
        return history;
    }

    /**
     * Save a chat message to Redis.
     */
    private void saveChatMessage(String chatKey, String sessionId, String role, String content) {
        if (redisTemplate == null) return;
        try {
            Map<String, Object> entry = new HashMap<>();
            entry.put("session_id", sessionId);
            entry.put("role", role);
            entry.put("text", content);
            entry.put("ts", System.currentTimeMillis() / 1000);
            redisTemplate.opsForList().rightPush(chatKey, objectMapper.writeValueAsString(entry));
            redisTemplate.expire(chatKey, 86400, TimeUnit.SECONDS);
        } catch (Exception e) {
            log.warn("Save chat message error: {}", e.getMessage());
        }
    }

    /**
     * Build clean messages list (extract system message for Ollama).
     */
    private List<Map<String, Object>> buildCleanMessages(List<Map<String, Object>> messages) {
        List<Map<String, Object>> clean = new ArrayList<>();
        for (Map<String, Object> m : messages) {
            if ("system".equals(m.get("role"))) {
                continue; // system handled separately via payload "system" field
            }
            Map<String, Object> msg = new HashMap<>();
            msg.put("role", m.get("role"));
            msg.put("content", m.getOrDefault("content", ""));
            if (m.containsKey("tool_calls")) msg.put("tool_calls", m.get("tool_calls"));
            if (m.containsKey("tool_call_id")) msg.put("tool_call_id", m.get("tool_call_id"));
            clean.add(msg);
        }
        return clean;
    }

    /**
     * Send an SSE event.
     */
    private void sendSseEvent(SseEmitter emitter, String type, Object data) {
        try {
            Map<String, Object> event = new HashMap<>();
            event.put("type", type);
            if (data instanceof String) {
                event.put("text", data);
            } else if (data instanceof Map) {
                event.putAll((Map<String, Object>) data);
            }
            emitter.send(SseEmitter.event().name("message").data(objectMapper.writeValueAsString(event)));
        } catch (Exception e) {
            log.warn("Failed to send SSE event {}: {}", type, e.getMessage());
        }
    }

    private void updateLastActive(Integer agentId) {
        var entity = new AgentEntity();
        entity.setId(agentId);
        entity.setUpdatedAt(java.time.LocalDateTime.now());
        agentMapper.updateById(entity);
    }

    private String discoverWorker(String agentType) {
        String workerService = "agent-" + agentType;
        String nacosUrl = buildNacosUrl();
        try {
            String resp = restTemplate.getForObject(
                    nacosUrl + "/ns/instance/list?serviceName={svc}&namespaceId={ns}&username={user}&password={pass}",
                    String.class,
                    workerService, nacosNamespace, "", ""
            );
            if (resp != null) {
                Map<String, Object> data = objectMapper.readValue(resp, new TypeReference<Map<String, Object>>() {});
                @SuppressWarnings("unchecked")
                List<Map<String, Object>> hosts = (List<Map<String, Object>>) data.getOrDefault("hosts", new ArrayList<>());
                if (!hosts.isEmpty()) {
                    Map<String, Object> h = hosts.get(0);
                    return "http://" + h.get("ip") + ":" + h.get("port");
                }
            }
        } catch (Exception e) {
            log.warn("Discover worker failed: {}", e.getMessage());
        }
        return "";
    }

    private String callMCPTool(String workerUrl, String toolName, Map<String, Object> arguments) {
        try {
            String msg = "[tool:" + toolName + "] " + objectMapper.writeValueAsString(arguments);
            Map<String, String> request = new HashMap<>();
            request.put("message", msg);
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            HttpEntity<Map<String, String>> entity = new HttpEntity<>(request, headers);
            String resp = restTemplate.postForObject(workerUrl + "/chat", entity, String.class);
            if (resp != null) {
                Map<String, Object> data = objectMapper.readValue(resp, new TypeReference<Map<String, Object>>() {});
                @SuppressWarnings("unchecked")
                Map<String, Object> innerData = (Map<String, Object>) data.getOrDefault("data", new HashMap<>());
                return (String) innerData.getOrDefault("response", resp);
            }
            return "Error: Empty response";
        } catch (Exception e) {
            return "Error calling tool: " + e.getMessage();
        }
    }

    private String buildNacosUrl() {
        String addr = nacosServerAddr;
        if (addr.startsWith("http://") || addr.startsWith("https://")) {
            addr = addr.substring(addr.indexOf("://") + 3);
        }
        return "http://" + addr + "/nacos/v1";
    }

    private String getAgentPrefix(String agentType) {
        Map<String, String> prefixMap = Map.of(
                "weather", "weather",
                "cloudflare", "cf",
                "k8s", "k8s"
        );
        return prefixMap.getOrDefault(agentType, agentType);
    }

    @SuppressWarnings("unchecked")
    private String extractContent(Map<String, Object> result) {
        List<Map<String, Object>> choices = (List<Map<String, Object>>) result.getOrDefault("choices", List.of());
        if (choices.isEmpty()) return "";
        Map<String, Object> choice = choices.get(0);
        Map<String, Object> msg = (Map<String, Object>) choice.getOrDefault("message", new HashMap<>());
        return (String) msg.getOrDefault("content", "");
    }

    @SuppressWarnings("unchecked")
    private String extractFinishReason(Map<String, Object> result) {
        List<Map<String, Object>> choices = (List<Map<String, Object>>) result.getOrDefault("choices", List.of());
        if (choices.isEmpty()) return "";
        return (String) choices.get(0).getOrDefault("finish_reason", "");
    }

    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> extractToolCalls(Map<String, Object> result) {
        List<Map<String, Object>> choices = (List<Map<String, Object>>) result.getOrDefault("choices", List.of());
        if (choices.isEmpty()) return null;
        Map<String, Object> msg = (Map<String, Object>) choices.get(0).getOrDefault("message", new HashMap<>());
        return (List<Map<String, Object>>) msg.get("tool_calls");
    }

    // ==================== Tool builders ====================

    private List<Map<String, Object>> buildK8sTools() {
        List<Map<String, Object>> tools = new ArrayList<>();
        tools.add(buildToolDef("k8s_list_pods", "List Kubernetes pods",
                Map.of("namespace", Map.of("type", "string")), List.of()));
        tools.add(buildToolDef("k8s_get_pod", "Get pod details",
                Map.of("name", Map.of("type", "string"), "namespace", Map.of("type", "string")), List.of("name")));
        tools.add(buildToolDef("k8s_pod_logs", "Get pod logs",
                Map.of("name", Map.of("type", "string"), "namespace", Map.of("type", "string"),
                        "tail_lines", Map.of("type", "integer"), "container", Map.of("type", "string")), List.of("name")));
        tools.add(buildToolDef("k8s_list_deployments", "List deployments",
                Map.of("namespace", Map.of("type", "string")), List.of()));
        tools.add(buildToolDef("k8s_list_services", "List services",
                Map.of("namespace", Map.of("type", "string")), List.of()));
        tools.add(buildToolDef("k8s_list_nodes", "List cluster nodes",
                Map.of(), List.of()));
        tools.add(buildToolDef("k8s_list_namespaces", "List namespaces",
                Map.of(), List.of()));
        tools.add(buildToolDef("k8s_list_events", "List recent events",
                Map.of("namespace", Map.of("type", "string"), "limit", Map.of("type", "integer")), List.of()));
        tools.add(buildToolDef("k8s_scale_deployment", "Scale a deployment",
                Map.of("name", Map.of("type", "string"), "replicas", Map.of("type", "integer"),
                        "namespace", Map.of("type", "string")), List.of("name", "replicas")));
        tools.add(buildToolDef("k8s_delete_pod", "Delete a pod",
                Map.of("name", Map.of("type", "string"), "namespace", Map.of("type", "string")), List.of("name")));
        tools.add(buildToolDef("k8s_pod_restart_reason", "Analyze why a pod restarted",
                Map.of("name", Map.of("type", "string"), "namespace", Map.of("type", "string")), List.of("name")));
        tools.add(buildToolDef("k8s_cluster_summary", "Get cluster resource summary",
                Map.of(), List.of()));
        return tools;
    }

    private List<Map<String, Object>> buildWeatherTools() {
        List<Map<String, Object>> tools = new ArrayList<>();
        tools.add(buildToolDef("get_current_weather", "Get current weather",
                Map.of("city", Map.of("type", "string"), "lang", Map.of("type", "string")), List.of("city")));
        tools.add(buildToolDef("get_weather_forecast", "Get weather forecast",
                Map.of("city", Map.of("type", "string"), "days", Map.of("type", "integer"),
                        "lang", Map.of("type", "string")), List.of("city")));
        return tools;
    }

    private Map<String, Object> buildToolDef(String name, String description,
                                             Map<String, Object> properties, List<String> required) {
        Map<String, Object> parameters = new HashMap<>();
        parameters.put("type", "object");
        parameters.put("properties", properties);
        parameters.put("required", required);
        Map<String, Object> function = new HashMap<>();
        function.put("name", name);
        function.put("description", description);
        function.put("parameters", parameters);
        return Map.of("type", "function", "function", function);
    }

    public void cancelStream(String sessionId) {
        var future = activeStreams.get(sessionId);
        if (future != null) {
            future.cancel(true);
            activeStreams.remove(sessionId);
        }
    }

    public Map<String, Object> getStatus(String sessionId) {
        Map<String, Object> status = new HashMap<>();
        status.put("sessionId", sessionId);
        status.put("active", activeStreams.containsKey(sessionId) && !activeStreams.get(sessionId).isDone());
        return status;
    }
}