package com.backend.agent.service;

import com.backend.agent.entity.AgentEntity;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestTemplate;

import java.sql.ResultSet;
import java.time.LocalDateTime;
import java.util.*;

@Service
public class MCPService {

    private static final Logger log = LoggerFactory.getLogger(MCPService.class);

    @Value("${spring.cloud.nacos.discovery.server-addr:192.168.86.9:8848}")
    private String nacosServerAddr;

    @Value("${spring.cloud.nacos.discovery.namespace:6c5b1db3-a808-4543-a87e-6642e372cb4f}")
    private String nacosNamespace;

    @Autowired
    private RestTemplate restTemplate;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired(required = false)
    private JdbcTemplate jdbcTemplate;

    // ==================== MCP CRUD (mcp table) ====================

    public List<Map<String, Object>> listMCPs() {
        if (jdbcTemplate == null) return List.of();
        try {
            return jdbcTemplate.query("SELECT * FROM mcp ORDER BY created_at DESC",
                    (ResultSet rs, int rowNum) -> {
                        Map<String, Object> row = new HashMap<>();
                        row.put("id", rs.getInt("id"));
                        row.put("name", rs.getString("name"));
                        row.put("description", rs.getString("description"));
                        String configStr = rs.getString("config");
                        try {
                            row.put("config", configStr != null ? objectMapper.readValue(configStr, Map.class) : Map.of());
                        } catch (Exception e) {
                            row.put("config", Map.of());
                        }
                        row.put("enabled", rs.getBoolean("enabled"));
                        row.put("createdAt", rs.getObject("created_at"));
                        row.put("updatedAt", rs.getObject("updated_at"));
                        return row;
                    });
        } catch (Exception e) {
            log.warn("List MCPs failed: {}", e.getMessage());
            return List.of();
        }
    }

    public Map<String, Object> createMCP(Map<String, Object> body) {
        if (jdbcTemplate == null) return null;
        String name = ((String) body.getOrDefault("name", "")).trim();
        String description = ((String) body.getOrDefault("description", "")).trim();
        String configStr;
        try {
            configStr = objectMapper.writeValueAsString(body.getOrDefault("config", Map.of()));
        } catch (Exception e) {
            configStr = "{}";
        }
        int enabled = Boolean.TRUE.equals(body.get("enabled")) || body.get("enabled") == null ? 1 : 0;
        jdbcTemplate.update(
                "INSERT INTO mcp (name, description, config, enabled, created_at, updated_at) VALUES (?, ?, ?, ?, UTC_TIMESTAMP(), UTC_TIMESTAMP())",
                name, description, configStr, enabled
        );
        return jdbcTemplate.queryForMap("SELECT * FROM mcp ORDER BY id DESC LIMIT 1");
    }

    @SuppressWarnings("unchecked")
    public Map<String, Object> updateMCP(Integer id, Map<String, Object> body) {
        if (jdbcTemplate == null) return null;
        // Check existence
        List<Map<String, Object>> existing = jdbcTemplate.query("SELECT * FROM mcp WHERE id = ?",
                (rs, rn) -> {
                    Map<String, Object> row = new HashMap<>();
                    row.put("id", rs.getInt("id"));
                    row.put("name", rs.getString("name"));
                    row.put("description", rs.getString("description"));
                    row.put("config", rs.getString("config"));
                    row.put("enabled", rs.getBoolean("enabled"));
                    return row;
                }, id);
        if (existing.isEmpty()) return null;

        String name = ((String) body.getOrDefault("name", existing.get(0).get("name"))).trim();
        String description = ((String) body.getOrDefault("description", existing.get(0).get("description"))).trim();
        String configStr;
        if (body.containsKey("config")) {
            try {
                configStr = objectMapper.writeValueAsString(body.get("config"));
            } catch (Exception e) {
                configStr = "{}";
            }
        } else {
            configStr = (String) existing.get(0).get("config");
        }
        int enabled = body.containsKey("enabled")
                ? (Boolean.TRUE.equals(body.get("enabled")) ? 1 : 0)
                : (Boolean.TRUE.equals(existing.get(0).get("enabled")) ? 1 : 0);

        jdbcTemplate.update(
                "UPDATE mcp SET name=?, description=?, config=?, enabled=?, updated_at=UTC_TIMESTAMP() WHERE id=?",
                name, description, configStr, enabled, id
        );
        return jdbcTemplate.queryForMap("SELECT * FROM mcp WHERE id = ?", id);
    }

    public boolean deleteMCP(Integer id) {
        if (jdbcTemplate == null) return false;
        int count = jdbcTemplate.update("DELETE FROM mcp WHERE id = ?", id);
        return count > 0;
    }

    // ==================== Nacos service discovery ====================

    /**
     * Discover MCP services from Nacos (services ending with "-mcp")
     */
    public List<Map<String, Object>> discoverServices() {
        List<Map<String, Object>> services = new ArrayList<>();
        String nacosUrl = buildNacosUrl();

        try {
            String resp = restTemplate.getForObject(
                    nacosUrl + "/ns/service/list?pageNo=1&pageSize=100&namespaceId={ns}&username={user}&password={pass}",
                    String.class,
                    nacosNamespace, "", ""
            );
            if (resp == null) return services;

            Map<String, Object> data = objectMapper.readValue(resp, new TypeReference<Map<String, Object>>() {});
            @SuppressWarnings("unchecked")
            List<String> doms = (List<String>) data.getOrDefault("doms", new ArrayList<>());

            for (String svc : doms) {
                if (svc.endsWith("-mcp")) {
                    try {
                        String instResp = restTemplate.getForObject(
                                nacosUrl + "/ns/instance/list?serviceName={svc}&namespaceId={ns}&username={user}&password={pass}",
                                String.class,
                                svc, nacosNamespace, "", ""
                        );
                        if (instResp != null) {
                            Map<String, Object> instData = objectMapper.readValue(instResp, new TypeReference<Map<String, Object>>() {});
                            @SuppressWarnings("unchecked")
                            List<Map<String, Object>> hosts = (List<Map<String, Object>>) instData.getOrDefault("hosts", new ArrayList<>());
                            if (!hosts.isEmpty()) {
                                Map<String, Object> h = hosts.get(0);
                                Map<String, Object> svcInfo = new HashMap<>();
                                svcInfo.put("name", svc);
                                svcInfo.put("url", "http://" + h.get("ip") + ":" + h.get("port") + "/sse");
                                svcInfo.put("ip", h.get("ip"));
                                svcInfo.put("port", h.get("port"));
                                svcInfo.put("healthy", h.getOrDefault("healthy", false));
                                services.add(svcInfo);
                            }
                        }
                    } catch (Exception e) {
                        Map<String, Object> svcInfo = new HashMap<>();
                        svcInfo.put("name", svc);
                        svcInfo.put("url", "");
                        svcInfo.put("ip", "");
                        svcInfo.put("port", 0);
                        svcInfo.put("healthy", false);
                        services.add(svcInfo);
                    }
                }
            }
        } catch (Exception e) {
            log.error("Nacos discover failed: {}", e.getMessage());
        }
        return services;
    }

    // ==================== Tool building ====================

    public List<Map<String, Object>> buildToolsForType(String agentType) {
        if ("k8s".equals(agentType)) {
            return buildK8sTools();
        } else if ("weather".equals(agentType)) {
            return buildWeatherTools();
        }
        return new ArrayList<>();
    }

    // ==================== Worker communication ====================

    public Map<String, Object> callWorkerChat(String workerUrl, String message) {
        try {
            Map<String, String> request = new HashMap<>();
            request.put("message", message);
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            HttpEntity<Map<String, String>> entity = new HttpEntity<>(request, headers);
            String resp = restTemplate.postForObject(workerUrl + "/chat", entity, String.class);
            if (resp != null) {
                return objectMapper.readValue(resp, new TypeReference<Map<String, Object>>() {});
            }
            return Collections.singletonMap("error", "Empty response");
        } catch (Exception e) {
            return Collections.singletonMap("error", "Error calling worker: " + e.getMessage());
        }
    }

    public String callMCPTool(String workerUrl, String toolName, Map<String, Object> arguments) {
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
                String response = (String) innerData.getOrDefault("response", resp);
                return response != null ? response : "Empty response";
            }
            return "Error: Empty response";
        } catch (Exception e) {
            return "Error calling tool: " + e.getMessage();
        }
    }

    public String discoverWorker(String agentType) {
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

    public String checkWorkerStatus(String agentType) {
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
                if (!hosts.isEmpty() && Boolean.TRUE.equals(hosts.get(0).get("healthy"))) {
                    return "online";
                }
            }
        } catch (Exception e) {
            log.warn("Check worker status failed: {}", e.getMessage());
        }
        return "offline";
    }

    private String buildNacosUrl() {
        String addr = nacosServerAddr;
        if (addr.startsWith("http://") || addr.startsWith("https://")) {
            addr = addr.substring(addr.indexOf("://") + 3);
        }
        return "http://" + addr + "/nacos/v1";
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
}