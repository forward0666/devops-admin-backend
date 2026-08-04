package com.backend.monitor.service;

import com.backend.monitor.entity.MonitorEntity;
import com.backend.monitor.mapper.MonitorMapper;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.net.HttpURLConnection;
import java.net.URI;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
@Service
public class MonitorService {

    @Autowired
    private MonitorMapper monitorMapper;

    @Value("${app.check.timeout:10000}")
    private int checkTimeout;

    private final Map<Long, String> checkResults = new ConcurrentHashMap<>();

    @PostConstruct
    public void init() {
        log.info("MonitorService initialized, checkTimeout={}ms", checkTimeout);
    }

    public List<MonitorEntity> listRules(Boolean enabled) {
        if (enabled != null) {
            return monitorMapper.selectList(
                new com.baomidou.mybatisplus.core.conditions.query.QueryWrapper<MonitorEntity>()
                    .eq("enabled", enabled));
        }
        return monitorMapper.selectList(null);
    }

    public MonitorEntity getRule(Long id) {
        return monitorMapper.selectById(id);
    }

    public void createRule(Map<String, Object> body) {
        var name = (String) body.get("name");
        var type = (String) body.get("type");
        var target = (String) body.get("target");
        if (name == null || name.isBlank()) throw new RuntimeException("name is required");
        if (type == null || type.isBlank()) throw new RuntimeException("type is required");
        if (target == null || target.isBlank()) throw new RuntimeException("target is required");

        var interval = body.containsKey("interval") ? ((Number) body.get("interval")).longValue() : 60000L;
        if (interval < 1000) throw new RuntimeException("interval must be >= 1000ms");

        var timeout = body.containsKey("timeout") ? ((Number) body.get("timeout")).intValue() : checkTimeout;
        if (timeout < 100) throw new RuntimeException("timeout must be >= 100ms");
        if (timeout > 300000) throw new RuntimeException("timeout must be <= 300000ms");

        var rule = new MonitorEntity();
        rule.setName(name);
        rule.setType(type);
        rule.setTarget(target);
        rule.setInterval(interval);
        rule.setTimeout(timeout);
        rule.setEnabled(body.containsKey("enabled") ? Boolean.TRUE.equals(body.get("enabled")) : true);
        rule.setDescription((String) body.getOrDefault("description", ""));
        rule.setStatus("unknown");
        rule.setCreatedAt(new Date());
        rule.setUpdatedAt(new Date());
        monitorMapper.insert(rule);
    }

    public void updateRule(Long id, Map<String, Object> body) {
        var existing = monitorMapper.selectById(id);
        if (existing == null) throw new RuntimeException("Rule not found");

        if (body.containsKey("name")) {
            var v = (String) body.get("name");
            if (v == null || v.isBlank()) throw new RuntimeException("name cannot be empty");
            existing.setName(v);
        }
        if (body.containsKey("type")) {
            var v = (String) body.get("type");
            if (v == null || v.isBlank()) throw new RuntimeException("type cannot be empty");
            existing.setType(v);
        }
        if (body.containsKey("target")) {
            var v = (String) body.get("target");
            if (v == null || v.isBlank()) throw new RuntimeException("target cannot be empty");
            existing.setTarget(v);
        }
        if (body.containsKey("interval")) {
            var v = ((Number) body.get("interval")).longValue();
            if (v < 1000) throw new RuntimeException("interval must be >= 1000ms");
            existing.setInterval(v);
        }
        if (body.containsKey("timeout")) {
            var v = ((Number) body.get("timeout")).intValue();
            if (v < 100) throw new RuntimeException("timeout must be >= 100ms");
            if (v > 300000) throw new RuntimeException("timeout must be <= 300000ms");
            existing.setTimeout(v);
        }
        if (body.containsKey("enabled")) existing.setEnabled(Boolean.TRUE.equals(body.get("enabled")));
        if (body.containsKey("description")) existing.setDescription((String) body.get("description"));
        existing.setUpdatedAt(new Date());
        monitorMapper.updateById(existing);
    }

    public void deleteRule(Long id) {
        var existing = monitorMapper.selectById(id);
        if (existing == null) throw new RuntimeException("Rule not found");
        monitorMapper.deleteById(id);
        checkResults.remove(id);
    }

    public Map<String, Object> checkRule(Long id) {
        var rule = monitorMapper.selectById(id);
        if (rule == null) throw new RuntimeException("Rule not found");

        long start = System.currentTimeMillis();
        Map<String, Object> result = new HashMap<>();
        result.put("ruleId", id);
        result.put("name", rule.getName());

        try {
            String status = performCheck(rule);
            long elapsed = System.currentTimeMillis() - start;
            rule.setStatus(status);
            rule.setLastCheck(new Date().toString());
            monitorMapper.updateById(rule);
            checkResults.put(id, status);

            result.put("status", status);
            result.put("elapsed", elapsed);
            log.info("Check {} ({}): {} in {}ms", rule.getName(), rule.getTarget(), status, elapsed);
        } catch (Exception e) {
            result.put("status", "error");
            result.put("error", e.getMessage());
            rule.setStatus("error");
            monitorMapper.updateById(rule);
        }
        return result;
    }

    public List<Map<String, Object>> checkAll() {
        var rules = monitorMapper.selectList(
            new com.baomidou.mybatisplus.core.conditions.query.QueryWrapper<MonitorEntity>()
                .eq("enabled", true));
        List<Map<String, Object>> results = new ArrayList<>();
        for (var rule : rules) {
            results.add(checkRule(rule.getId()));
        }
        return results;
    }

    public Map<String, Object> getStatusSummary() {
        var all = monitorMapper.selectList(null);
        long up = all.stream().filter(r -> "up".equals(r.getStatus())).count();
        long down = all.stream().filter(r -> "down".equals(r.getStatus())).count();
        long error = all.stream().filter(r -> "error".equals(r.getStatus())).count();
        long unknown = all.stream().filter(r -> !Set.of("up", "down", "error").contains(r.getStatus())).count();

        return Map.of(
            "total", all.size(),
            "up", up,
            "down", down,
            "error", error,
            "unknown", unknown
        );
    }

    @Scheduled(fixedRateString = "${app.check.interval:60000}")
    public void scheduledCheck() {
        log.debug("Scheduled check starting...");
        try { checkAll(); } catch (Exception e) {
            log.error("Scheduled check failed: {}", e.getMessage());
        }
    }

    private String performCheck(MonitorEntity rule) throws Exception {
        String type = rule.getType();
        String target = rule.getTarget();
        int timeout = rule.getTimeout() != null ? rule.getTimeout() : checkTimeout;

        if ("http".equals(type) || "https".equals(type)) {
            var url = new URI(target).toURL();
            var conn = (HttpURLConnection) url.openConnection();
            conn.setConnectTimeout(timeout);
            conn.setReadTimeout(timeout);
            conn.setRequestMethod("GET");
            conn.setInstanceFollowRedirects(true);
            int code = conn.getResponseCode();
            conn.disconnect();
            return code < 500 ? "up" : "down";
        } else if ("ping".equals(type)) {
            // Simplified: just try HTTP
            var url = new URI("http://" + target).toURL();
            var conn = (HttpURLConnection) url.openConnection();
            conn.setConnectTimeout(timeout);
            conn.setReadTimeout(timeout);
            int code = conn.getResponseCode();
            conn.disconnect();
            return code < 500 ? "up" : "down";
        } else if ("tcp".equals(type)) {
            try (var sock = new java.net.Socket()) {
                String host = target.contains(":") ? target.split(":")[0] : target;
                int port = target.contains(":") ? Integer.parseInt(target.split(":")[1]) : 80;
                sock.connect(new java.net.InetSocketAddress(host, port), timeout);
                return "up";
            }
        }
        return "unknown";
    }
}