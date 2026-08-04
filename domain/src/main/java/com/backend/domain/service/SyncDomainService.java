package com.backend.domain.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Service;

import java.sql.ResultSet;
import java.util.*;

@Slf4j
@Service
public class SyncDomainService {

    @Autowired
    private JdbcTemplate jdbcTemplate;

    public List<Map<String, Object>> listRules() {
        var rows = jdbcTemplate.queryForList(
            "SELECT id, name, group_id, project_id, env, type, description, enabled, status, last_check, created_at, updated_at FROM sync_domain_rule ORDER BY id DESC");
        for (var r : rows) {
            Object enabled = r.get("enabled");
            r.put("enabled", enabled instanceof Boolean ? enabled : (enabled instanceof Number n && n.intValue() == 1));
        }
        return rows;
    }

    public Map<String, Object> getRule(Long ruleId) {
        var rows = jdbcTemplate.queryForList("SELECT * FROM sync_domain_rule WHERE id = ?", ruleId);
        if (rows.isEmpty()) return null;
        var r = rows.get(0);
        Object enabled = r.get("enabled");
        r.put("enabled", enabled instanceof Boolean ? enabled : (enabled instanceof Number n && n.intValue() == 1));
        return r;
    }

    public void createRule(Map<String, Object> body) {
        String name = ((String) body.getOrDefault("name", "")).strip();
        String groupId = (String) body.getOrDefault("group_id", "");
        String projectId = (String) body.getOrDefault("project_id", "");
        String env = (String) body.getOrDefault("env", "prod");
        String type = (String) body.getOrDefault("type", "web");
        String description = (String) body.getOrDefault("description", "");
        int enabled = Boolean.TRUE.equals(body.get("enabled")) ? 1 : 0;

        if (name.isBlank()) throw new RuntimeException("Name is required");
        if (groupId.isBlank() || projectId.isBlank()) throw new RuntimeException("group_id and project_id are required");

        jdbcTemplate.update(
            "INSERT INTO sync_domain_rule (name, group_id, project_id, env, type, description, enabled) VALUES (?, ?, ?, ?, ?, ?, ?)",
            name, groupId, projectId, env, type, description, enabled);
    }

    public void updateRule(Long ruleId, Map<String, Object> body) {
        var existing = jdbcTemplate.queryForList("SELECT id FROM sync_domain_rule WHERE id = ?", ruleId);
        if (existing.isEmpty()) throw new RuntimeException("Rule not found");

        String name = ((String) body.getOrDefault("name", "")).strip();
        if (name.isBlank()) throw new RuntimeException("Name is required");

        jdbcTemplate.update(
            "UPDATE sync_domain_rule SET name=?, group_id=?, project_id=?, env=?, type=?, description=?, enabled=?, updated_at=UTC_TIMESTAMP() WHERE id=?",
            name,
            body.getOrDefault("group_id", ""),
            body.getOrDefault("project_id", ""),
            body.getOrDefault("env", "prod"),
            body.getOrDefault("type", "web"),
            body.getOrDefault("description", ""),
            Boolean.TRUE.equals(body.get("enabled")) ? 1 : 0,
            ruleId);
    }

    public void deleteRule(Long ruleId) {
        int count = jdbcTemplate.update("DELETE FROM sync_domain_rule WHERE id = ?", ruleId);
        if (count == 0) throw new RuntimeException("Rule not found");
    }

    public Map<String, Object> checkRule(Long ruleId) {
        // Simplified version - sync domains from group to project via MongoDB
        // Full implementation requires MongoDB + MySQL cross-database operations
        var rule = jdbcTemplate.queryForList("SELECT * FROM sync_domain_rule WHERE id = ?", ruleId);
        if (rule.isEmpty()) throw new RuntimeException("Rule not found");

        log.info("SyncDomain check: rule {}", ruleId);
        // In production, this would:
        // 1. Query domain_group by group_id in MongoDB
        // 2. Get zone_ids from domain_meta 
        // 3. Get zone names from cloudflare account_{id}_zones
        // 4. Get domain names from domain collection
        // 5. Write to project.domains

        Map<String, Object> result = new HashMap<>();
        result.put("synced", 0);
        result.put("message", "check triggered (full implementation needs MongoDB service)");
        jdbcTemplate.update("UPDATE sync_domain_rule SET status='ok', last_check=UTC_TIMESTAMP() WHERE id=?", ruleId);
        return result;
    }
}