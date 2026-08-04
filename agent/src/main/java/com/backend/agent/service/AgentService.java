package com.backend.agent.service;

import com.backend.agent.entity.AgentEntity;
import com.backend.agent.entity.ModelEntity;
import com.backend.agent.mapper.AgentMapper;
import com.backend.agent.mapper.ModelMapper;
import com.backend.utils.exception.BizException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

import static org.springframework.data.mongodb.core.query.Criteria.where;

@Slf4j
@Service
@RequiredArgsConstructor
public class AgentService {

    private final AgentMapper agentMapper;
    private final ModelMapper modelMapper;
    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper;
    private final StringRedisTemplate redisTemplate;
    private final MongoTemplate mongoTemplate;

    @Value("${spring.cloud.nacos.discovery.server-addr:192.168.86.9:8848}")
    private String nacosServerAddr;

    @Value("${spring.cloud.nacos.discovery.namespace:6c5b1db3-a808-4543-a87e-6642e372cb4f}")
    private String nacosNamespace;

    // ==================== CRUD ====================

    public List<AgentEntity> list() {
        return agentMapper.selectList(null);
    }

    public AgentEntity getById(Integer id) {
        return agentMapper.selectById(id);
    }

    public AgentEntity create(AgentEntity entity) {
        LocalDateTime now = LocalDateTime.now();
        entity.setCreatedAt(now);
        entity.setUpdatedAt(now);
        if (entity.getStatus() == null) {
            entity.setStatus("offline");
        }
        if (entity.getEnabled() == null) {
            entity.setEnabled(true);
        }
        agentMapper.insert(entity);
        return entity;
    }

    public AgentEntity update(Integer id, AgentEntity entity) {
        entity.setId(id);
        entity.setUpdatedAt(LocalDateTime.now());
        agentMapper.updateById(entity);
        return agentMapper.selectById(id);
    }

    public boolean delete(Integer id) {
        return agentMapper.deleteById(id) > 0;
    }

    public List<AgentEntity> search(String keyword) {
        return agentMapper.selectList(
                new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<AgentEntity>()
                        .like(AgentEntity::getName, keyword)
                        .or()
                        .like(AgentEntity::getType, keyword)
                        .or()
                        .like(AgentEntity::getDescription, keyword)
        );
    }

    public void updateAgentStatus(Integer id, String status) {
        var entity = new AgentEntity();
        entity.setId(id);
        entity.setStatus(status);
        entity.setLastActiveAt(LocalDateTime.now());
        entity.setUpdatedAt(LocalDateTime.now());
        agentMapper.updateById(entity);
    }

    // ==================== Chat ====================

    /**
     * Chat with an agent — either keyword mode or LLM mode with tool calls.
     * Matches POST /agents/{agent_id}/chat from agent.py
     */
    public Map<String, Object> chat(Integer agentId, String message, String sessionId) {
        log.info("Chat: agent={}, msg='{}'", agentId, message.substring(0, Math.min(message.length(), 50)));

        AgentEntity agent = agentMapper.selectById(agentId);
        if (agent == null) {
            throw new BizException(404, "Agent not found");
        }

        String agentType = agent.getType();
        Integer modelId = agent.getModelId();

        // Discover worker
        String workerUrl = discoverWorker(agentType);
        log.info("Worker: agent-{} -> {}", agentType, workerUrl);
        if (workerUrl.isEmpty()) {
            throw new BizException(502, "Agent worker 'agent-" + agentType + "' not found");
        }

        // No model -> keyword mode
        if (modelId == null) {
            log.info("Mode: keyword (no model)");
            try {
                Map<String, String> request = new HashMap<>();
                request.put("message", message);
                request.put("session_id", sessionId);
                HttpHeaders headers = new HttpHeaders();
                headers.setContentType(MediaType.APPLICATION_JSON);
                HttpEntity<Map<String, String>> entity = new HttpEntity<>(request, headers);
                String resp = restTemplate.postForObject(workerUrl + "/chat", entity, String.class);
                if (resp != null) {
                    updateAgentStatus(agentId, "online");
                    return objectMapper.readValue(resp, new TypeReference<Map<String, Object>>() {});
                }
                throw new BizException(502, "Empty response from worker");
            } catch (BizException e) {
                throw e;
            } catch (Exception e) {
                throw new BizException(502, "Cannot connect to agent-" + agentType + ": " + e.getMessage());
            }
        }

        // LLM mode
        ModelEntity modelConfig = modelMapper.selectById(modelId);
        if (modelConfig == null || !Boolean.TRUE.equals(modelConfig.getEnabled())) {
            throw new BizException(400, "Model not found or disabled");
        }
        log.info("Mode: LLM ({}/{})", modelConfig.getName(), modelConfig.getModel());

        // Build tools for this agent type
        List<Map<String, Object>> tools = buildToolsForType(agentType);
        log.info("Tools: {} defined", tools.size());

        // Build system prompt
        String systemPrompt = "You are a helpful " + agentType + " assistant. " +
                "You have access to tools to manage " + agentType + " resources. " +
                "Always use the appropriate tool to answer user questions. " +
                "When showing results, format them in a clean, readable way. " +
                "For lists, use a table-like format. For single items, show key details clearly.";

        List<Map<String, Object>> messages = new ArrayList<>();
        messages.add(Map.of("role", "system", "content", systemPrompt));
        messages.add(Map.of("role", "user", "content", message));

        boolean useTools = !tools.isEmpty();
        for (int step = 0; step < 3; step++) {
            log.info("LLM call #{}, use_tools={}", step + 1, useTools);
            Map<String, Object> llmResult = callLLM(modelConfig, messages, useTools ? tools : null);

            // Handle error
            Map<String, Object> error = (Map<String, Object>) llmResult.get("error");
            if (error != null) {
                if (useTools && error.containsKey("message") && ((String) error.get("message")).contains("400")) {
                    log.warn("400 with tools -> retry without tools");
                    useTools = false;
                    llmResult = callLLM(modelConfig, messages, null);
                    if (llmResult.containsKey("error")) {
                        log.error("Retry failed: {}", llmResult.get("error"));
                        throw new BizException(502, (String) llmResult.get("error"));
                    }
                    String content = extractContent(llmResult);
                    if (content != null && !content.isEmpty()) {
                        updateAgentStatus(agentId, "online");
                        return Map.of("response", content, "tool", "llm");
                    }
                    return Map.of("response", "No response from LLM", "tool", "llm");
                } else {
                    log.error("LLM error: {}", llmResult.get("error"));
                    throw new BizException(502, (String) llmResult.get("error"));
                }
            }

            String finishReason = extractFinishReason(llmResult);
            List<Map<String, Object>> toolCalls = extractToolCalls(llmResult);
            String content = extractContent(llmResult);
            log.info("finish_reason={}, content='{}'", finishReason, content != null ? content.substring(0, Math.min(content.length(), 80)) : "");

            if ("tool_calls".equals(finishReason) && toolCalls != null && !toolCalls.isEmpty()) {
                Map<String, Object> assistantMsg = new HashMap<>();
                assistantMsg.put("role", "assistant");
                assistantMsg.put("content", content);
                assistantMsg.put("tool_calls", toolCalls);
                messages.add(assistantMsg);

                for (Map<String, Object> tc : toolCalls) {
                    Map<String, Object> fn = (Map<String, Object>) tc.getOrDefault("function", new HashMap<>());
                    String toolName = (String) fn.getOrDefault("name", "");
                    String argsStr = (String) fn.getOrDefault("arguments", "{}");
                    Map<String, Object> toolArgs;
                    try {
                        toolArgs = objectMapper.readValue(argsStr, new TypeReference<Map<String, Object>>() {});
                    } catch (Exception e) {
                        toolArgs = new HashMap<>();
                    }
                    log.info("Calling tool: {}({})", toolName, toolArgs);
                    String toolResult = callMCPTool(workerUrl, toolName, toolArgs);
                    log.info("Tool result: {} chars", toolResult.length());

                    Map<String, Object> toolMsg = new HashMap<>();
                    toolMsg.put("role", "tool");
                    toolMsg.put("tool_call_id", tc.getOrDefault("id", ""));
                    toolMsg.put("content", toolResult);
                    messages.add(toolMsg);
                }
                continue;
            }

            if (content != null && !content.isEmpty()) {
                updateAgentStatus(agentId, "online");
                return Map.of("response", content, "tool", "llm");
            }
        }

        return Map.of("response", "Max tool calls reached", "tool", "llm");
    }

    // ==================== Chat History (Redis) ====================

    /**
     * Get chat history from agent worker.
     * Matches GET /agents/{id}/chat/history from agent.py
     */
    public List<Object> getChatHistory(Integer agentId, String sessionId) {
        AgentEntity agent = agentMapper.selectById(agentId);
        if (agent == null) {
            return List.of();
        }
        String workerUrl = discoverWorker(agent.getType());
        if (workerUrl.isEmpty()) {
            return List.of();
        }
        try {
            String resp = restTemplate.getForObject(
                    workerUrl + "/chat/history?session_id={sessionId}",
                    String.class,
                    sessionId
            );
            if (resp != null) {
                Map<String, Object> result = objectMapper.readValue(resp, new TypeReference<Map<String, Object>>() {});
                return (List<Object>) result.getOrDefault("data", List.of());
            }
        } catch (Exception e) {
            log.warn("Get chat history error: {}", e.getMessage());
        }
        return List.of();
    }

    /**
     * Get chat sessions from Redis.
     * Matches GET /agents/{id}/chat/sessions from agent.py
     */
    public List<Map<String, Object>> getChatSessions(Integer agentId) {
        AgentEntity agent = agentMapper.selectById(agentId);
        if (agent == null) {
            return List.of();
        }
        String agentType = agent.getType();
        String prefix = getAgentPrefix(agentType);

        try {
            String pattern = "agent:" + prefix + ":chat:*";
            Set<String> keys = redisTemplate.keys(pattern);
            if (keys == null || keys.isEmpty()) {
                return List.of();
            }

            List<Map<String, Object>> sessions = new ArrayList<>();
            for (String key : keys) {
                String sessionId = key.replace("agent:" + prefix + ":chat:", "");
                // Get first message as title
                String firstMsg = redisTemplate.opsForList().index(key, 0);
                String title = "";
                String timeStr = "";
                if (firstMsg != null) {
                    try {
                        Map<String, Object> entry = objectMapper.readValue(firstMsg, new TypeReference<Map<String, Object>>() {});
                        String text = (String) entry.getOrDefault("text", "");
                        title = text.length() > 30 ? text.substring(0, 30) : text;
                        Object ts = entry.get("ts");
                        if (ts instanceof Number) {
                            long timestamp = ((Number) ts).longValue();
                            timeStr = new java.text.SimpleDateFormat("MM-dd HH:mm")
                                    .format(new java.util.Date(timestamp * 1000));
                        }
                    } catch (Exception ignored) {}
                }
                Map<String, Object> s = new HashMap<>();
                s.put("id", sessionId);
                s.put("title", title);
                s.put("time", timeStr);
                sessions.add(s);
            }
            sessions.sort((a, b) -> ((String) b.get("id")).compareTo((String) a.get("id")));
            return sessions;
        } catch (Exception e) {
            log.warn("Get sessions error: {}", e.getMessage());
            return List.of();
        }
    }

    /**
     * Delete a chat session from Redis.
     * Matches DELETE /agents/{id}/chat/sessions/{sessionId} from agent.py
     */
    public void deleteChatSession(Integer agentId, String sessionId) {
        AgentEntity agent = agentMapper.selectById(agentId);
        if (agent == null) return;

        String agentType = agent.getType();
        String prefix = getAgentPrefix(agentType);
        String key = "agent:" + prefix + ":chat:" + sessionId;
        try {
            redisTemplate.delete(key);
            log.info("Deleted session: {}", key);
        } catch (Exception e) {
            log.warn("Delete session error: {}", e.getMessage());
        }
    }

    // ==================== Agent Tools / Files (MongoDB) ====================

    /**
     * Get agent tools/prompts from MongoDB.
     * Matches GET /agents/{id}/tools from agent.py
     */
    public Map<String, Object> getAgentTools(Integer agentId) {
        try {
            Query query = Query.query(where("agent_id").is(agentId));
            Map<String, Object> doc = mongoTemplate.findOne(query, Map.class, "agent_tools");
            if (doc == null) {
                AgentEntity agent = agentMapper.selectById(agentId);
                String agentName = agent != null ? agent.getName() : "Agent";
                String agentType = agent != null ? agent.getType() : "";
                doc = new HashMap<>();
                doc.put("agent_id", agentId);
                doc.put("files", getDefaultFiles(agentName, agentType));
                mongoTemplate.insert(doc, "agent_tools");
            }
            doc.remove("_id");
            return doc;
        } catch (Exception e) {
            log.warn("Get tools error: {}", e.getMessage());
            return new HashMap<>();
        }
    }

    /**
     * Update agent tools/prompts in MongoDB.
     * Matches PUT /agents/{id}/tools from agent.py
     */
    public void updateAgentTools(Integer agentId, Map<String, Object> body) {
        try {
            body.put("agent_id", agentId);
            body.put("updated_at", System.currentTimeMillis() / 1000);
            Query query = Query.query(where("agent_id").is(agentId));
            Update update = new Update();
            for (Map.Entry<String, Object> entry : body.entrySet()) {
                update.set(entry.getKey(), entry.getValue());
            }
            mongoTemplate.upsert(query, update, "agent_tools");
        } catch (Exception e) {
            log.error("Update tools error", e);
            throw new BizException(500, e.getMessage());
        }
    }

    // ==================== Private helpers ====================

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
            log.warn("Discover worker failed for {}: {}", agentType, e.getMessage());
        }
        return "";
    }

    private Map<String, Object> callLLM(ModelEntity modelConfig, List<Map<String, Object>> messages, List<Map<String, Object>> tools) {
        String baseUrl = modelConfig.getBaseUrl();
        if (baseUrl != null && baseUrl.endsWith("/")) {
            baseUrl = baseUrl.substring(0, baseUrl.length() - 1);
        }
        String apiKey = modelConfig.getApiKey();
        String modelName = modelConfig.getModel();

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        if (apiKey != null && !apiKey.isEmpty()) {
            headers.setBearerAuth(apiKey);
        }

        String systemContent = null;
        List<Map<String, Object>> cleanMessages = new ArrayList<>();
        for (Map<String, Object> m : messages) {
            if ("system".equals(m.get("role"))) {
                systemContent = (String) m.get("content");
            } else {
                Map<String, Object> clean = new HashMap<>();
                clean.put("role", m.get("role"));
                clean.put("content", m.getOrDefault("content", ""));
                if (m.containsKey("tool_calls")) {
                    clean.put("tool_calls", m.get("tool_calls"));
                }
                if (m.containsKey("tool_call_id")) {
                    clean.put("tool_call_id", m.get("tool_call_id"));
                }
                cleanMessages.add(clean);
            }
        }

        Map<String, Object> payload = new HashMap<>();
        payload.put("model", modelName);
        payload.put("messages", cleanMessages);
        payload.put("temperature", 0.3);
        payload.put("max_tokens", 2000);
        if (systemContent != null) {
            payload.put("system", systemContent);
        }
        if (tools != null && !tools.isEmpty()) {
            payload.put("tools", tools);
        }

        log.info("LLM request: model={}, msgs={}, tools={}", modelName, cleanMessages.size(), tools != null ? tools.size() : 0);
        try {
            HttpEntity<Map<String, Object>> entity = new HttpEntity<>(payload, headers);
            String resp = restTemplate.postForObject(baseUrl + "/chat/completions", entity, String.class);
            if (resp != null) {
                return objectMapper.readValue(resp, new TypeReference<Map<String, Object>>() {});
            }
            return Map.of("error", Map.of("message", "Empty LLM response"));
        } catch (Exception e) {
            log.error("LLM call error: {}: {}", e.getClass().getSimpleName(), e.getMessage());
            return Map.of("error", Map.of("message", e.getClass().getSimpleName() + ": " + e.getMessage()));
        }
    }

    private String callMCPTool(String workerUrl, String toolName, Map<String, Object> arguments) {
        try {
            String argsJson = objectMapper.writeValueAsString(arguments);
            String msg = "[tool:" + toolName + "] " + argsJson;
            Map<String, String> request = new HashMap<>();
            request.put("message", msg);
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            HttpEntity<Map<String, String>> entity = new HttpEntity<>(request, headers);
            String resp = restTemplate.postForObject(workerUrl + "/chat", entity, String.class);
            if (resp != null) {
                Map<String, Object> data = objectMapper.readValue(resp, new TypeReference<Map<String, Object>>() {});
                Map<String, Object> innerData = (Map<String, Object>) data.getOrDefault("data", new HashMap<>());
                String response = (String) innerData.getOrDefault("response", resp);
                return response != null ? response : "Empty response";
            }
            return "Error: Empty response";
        } catch (Exception e) {
            return "Error calling tool: " + e.getMessage();
        }
    }

    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> buildToolsForType(String agentType) {
        if ("k8s".equals(agentType)) {
            return buildK8sTools();
        } else if ("weather".equals(agentType)) {
            return buildWeatherTools();
        }
        return new ArrayList<>();
    }

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

    private String extractContent(Map<String, Object> llmResult) {
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> choices = (List<Map<String, Object>>) llmResult.getOrDefault("choices", List.of());
        if (choices.isEmpty()) return "";
        Map<String, Object> choice = choices.get(0);
        Map<String, Object> msg = (Map<String, Object>) choice.getOrDefault("message", new HashMap<>());
        return (String) msg.getOrDefault("content", "");
    }

    @SuppressWarnings("unchecked")
    private String extractFinishReason(Map<String, Object> llmResult) {
        List<Map<String, Object>> choices = (List<Map<String, Object>>) llmResult.getOrDefault("choices", List.of());
        if (choices.isEmpty()) return "";
        return (String) choices.get(0).getOrDefault("finish_reason", "");
    }

    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> extractToolCalls(Map<String, Object> llmResult) {
        List<Map<String, Object>> choices = (List<Map<String, Object>>) llmResult.getOrDefault("choices", List.of());
        if (choices.isEmpty()) return null;
        Map<String, Object> msg = (Map<String, Object>) choices.get(0).getOrDefault("message", new HashMap<>());
        return (List<Map<String, Object>>) msg.get("tool_calls");
    }

    private String getAgentPrefix(String agentType) {
        Map<String, String> prefixMap = Map.of(
                "weather", "weather",
                "cloudflare", "cf",
                "k8s", "k8s"
        );
        return prefixMap.getOrDefault(agentType, agentType);
    }

    private String buildNacosUrl() {
        String addr = nacosServerAddr;
        if (addr.startsWith("http://") || addr.startsWith("https://")) {
            addr = addr.substring(addr.indexOf("://") + 3);
        }
        return "http://" + addr + "/nacos/v1";
    }

    private List<Map<String, Object>> getDefaultFiles(String agentName, String agentType) {
        List<Map<String, Object>> files = new ArrayList<>();
        files.add(Map.of("name", "AGENTS.md", "content",
                "# " + agentName + "\n\nAgent configuration and behavior rules.\n\n## Behavior Rules\n- Show exact tool data, do not rename/translate/fabricate fields\n- Account names, zone names, IDs must be shown as-is\n"));
        files.add(Map.of("name", "SOUL.md", "content", "# Soul\n\nDefine the agent's personality and tone.\n"));
        files.add(Map.of("name", "TOOLS.md", "content", "# Tools\n\nAvailable tools and their usage for " + agentType + " agent.\n"));
        files.add(Map.of("name", "IDENTITY.md", "content", "# Identity\n\n- Name: " + agentName + "\n- Type: " + agentType + "\n"));
        files.add(Map.of("name", "USER.md", "content", "# User Context\n\nUser-specific settings and preferences.\n"));
        files.add(Map.of("name", "MEMORY.md", "content", "# Memory\n\nLong-term memory and learnings.\n"));
        return files;
    }
}