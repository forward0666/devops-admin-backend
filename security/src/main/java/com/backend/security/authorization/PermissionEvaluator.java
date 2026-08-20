package com.backend.security.authorization;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

@Slf4j
@Component
@RequiredArgsConstructor
public class PermissionEvaluator {

    private final StringRedisTemplate redis;
    private final JdbcTemplate jdbc;
    private final ObjectMapper objectMapper = new ObjectMapper();

    private static final String KEY_PREFIX = "auth:policy:";
    private static final long CACHE_TTL = 3600;

    public boolean hasPermission(Long userId, String action, String resource) {
        List<PolicyStatement> policies = getUserPolicies(userId);
        return evaluate(policies, action, resource);
    }

    public boolean hasPermission(Long userId, String action) {
        return hasPermission(userId, action, "*");
    }

    public List<String> getVisibleResources(Long userId, String resourceType) {
        List<PolicyStatement> policies = getUserPolicies(userId);
        List<String> visibleIds = new ArrayList<>();
        for (PolicyStatement p : policies) {
            if (p.getEffect() == PolicyStatement.Effect.Deny) continue;
            for (String r : p.getResources()) {
                if (r.startsWith(resourceType + ":")) {
                    String id = r.substring(resourceType.length() + 1);
                    if (!"*".equals(id)) visibleIds.add(id);
                    else return Collections.emptyList();
                }
            }
        }
        return visibleIds;
    }

    private boolean evaluate(List<PolicyStatement> policies, String action, String resource) {
        boolean hasAllow = false;
        for (PolicyStatement p : policies) {
            if (!matchAction(p.getActions(), action)) continue;
            if (!matchResource(p.getResources(), resource)) continue;
            if (p.getEffect() == PolicyStatement.Effect.Deny) return false;
            hasAllow = true;
        }
        return hasAllow;
    }

    private boolean matchAction(List<String> policyActions, String action) {
        for (String pa : policyActions) {
            if (wildcardMatch(pa, action)) return true;
        }
        return false;
    }

    private boolean matchResource(List<String> policyResources, String resource) {
        for (String pr : policyResources) {
            if ("*".equals(pr)) return true;
            if (wildcardMatch(pr, resource)) return true;
        }
        return false;
    }

    private boolean wildcardMatch(String pattern, String value) {
        if (pattern == null || value == null) return false;
        String regex = pattern.replace(".", "\\.").replace("?", ".").replace("*", ".*");
        return value.matches(regex);
    }

    private List<PolicyStatement> getUserPolicies(Long userId) {
        String key = KEY_PREFIX + userId;
        String cached = (String) redis.opsForHash().entries(key).get("policies");
        if (cached != null) {
            try { return objectMapper.readValue(cached, new TypeReference<List<PolicyStatement>>() {}); }
            catch (Exception e) { log.warn("Failed to parse cached policies", e); }
        }
        List<PolicyStatement> policies = loadPoliciesFromDb(userId);
        try {
            redis.opsForHash().put(key, "policies", objectMapper.writeValueAsString(policies));
            redis.expire(key, CACHE_TTL, TimeUnit.SECONDS);
        } catch (Exception e) { log.warn("Failed to cache policies", e); }
        return policies;
    }

    private List<PolicyStatement> loadPoliciesFromDb(Long userId) {
        // 查询用户的所有角色 → 角色的所有权限策略
        List<Map<String, Object>> rows = jdbc.queryForList(
            "SELECT rp.permission_code, rp.resource_scope, rp.effect " +
            "FROM sys_user_role ur " +
            "JOIN sys_role_permission rp ON ur.role_id = rp.role_id " +
            "WHERE ur.user_id = ?", userId);

        Map<String, PolicyStatement> merged = new HashMap<>();
        for (Map<String, Object> row : rows) {
            String code = (String) row.get("permission_code");
            String scope = (String) row.get("resource_scope");
            if (scope == null || scope.isBlank()) scope = "*";
            String effect = (String) row.get("effect");
            if (effect == null) effect = "Allow";

            PolicyStatement.Effect eff = "Deny".equals(effect) 
                ? PolicyStatement.Effect.Deny 
                : PolicyStatement.Effect.Allow;

            // 通配符 code="*" 表示所有权限
            if ("*".equals(code)) {
                return List.of(PolicyStatement.builder()
                    .effect(eff)
                    .actions(List.of("*"))
                    .resources(List.of(scope))
                    .build());
            }

            merged.merge(code, PolicyStatement.builder()
                .effect(eff)
                .actions(List.of(code))
                .resources(List.of(scope))
                .build(), (a, b) -> {
                    // Deny 优先
                    if (b.getEffect() == PolicyStatement.Effect.Deny) return b;
                    return a;
                });
        }

        return new ArrayList<>(merged.values());
    }
}