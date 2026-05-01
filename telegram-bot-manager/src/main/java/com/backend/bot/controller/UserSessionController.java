package com.backend.bot.controller;

import com.backend.bot.entity.UserSessionEntity;
import com.backend.bot.service.RedisUserSessionService;
import com.backend.bot.service.UserSessionService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import network.HttpResponseUtils;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Mono;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.HashMap;
import java.util.Map;

/**
 * 用户会话管理控制器
 * 
 * 提供API接口用于管理用户会话状态。
 */
@RestController
@RequiredArgsConstructor
@Slf4j
@RequestMapping("/sessions")
public class UserSessionController {

    private final UserSessionService userSessionService;
    private final RedisUserSessionService redisUserSessionService;

    /**
     * 获取用户会话状态
     * 
     * @param userId 用户ID
     * @return 用户会话状态
     */
    @GetMapping("/user/{userId}")
    public Mono<ResponseEntity<Map<String, Object>>> getUserSession(@PathVariable Long userId) {
        return userSessionService.getUserSession(userId)
                .map(session -> {
                    Map<String, Object> sessionData = convertSessionToMap(session);
                    return HttpResponseUtils.ok(sessionData);
                })
                .defaultIfEmpty(HttpResponseUtils.ok(Map.of("userId", userId, "hasSession", false)));
    }

    /**
     * 清除用户会话
     * 
     * @param userId 用户ID
     * @return 清除结果
     */
    @DeleteMapping("/user/{userId}")
    public Mono<ResponseEntity<Map<String, Object>>> clearUserSession(@PathVariable Long userId) {
        return userSessionService.clearUserSession(userId)
                .then(redisUserSessionService.cancelPendingDeletion(userId))
                .map(v -> {
                    Map<String, Object> data = new HashMap<>();
                    data.put("userId", userId);
                    data.put("cleared", true);
                    return HttpResponseUtils.ok(data);
                });
    }

    /**
     * 获取会话统计信息
     * 
     * @return 会话统计信息
     */
    @GetMapping("/stats")
    public Mono<ResponseEntity<Map<String, Object>>> getSessionStats() {
        // 扫描 Redis 中的 user:session:* keys 来统计
        return redisTemplate.keys("user:session:*")
                .count()
                .map(total -> {
                    Map<String, Object> stats = new HashMap<>();
                    stats.put("totalSessions", total);
                    stats.put("activeSessions", total); // 所有在 Redis 中的都是 active
                    stats.put("expiredSessions", 0);
                    return HttpResponseUtils.ok(stats);
                });
    }

    /**
     * 获取所有活跃会话
     * 
     * @param limit 限制数量
     * @return 活跃会话列表
     */
    @GetMapping("/active")
    public Mono<ResponseEntity<Map<String, Object>>> getActiveSessions(
            @RequestParam(defaultValue = "100") Integer limit) {
        return redisTemplate.keys("user:session:*")
                .take(limit)
                .flatMap(key -> redisTemplate.opsForValue().get(key)
                        .map(value -> {
                            Map<String, Object> session = new HashMap<>();
                            session.put("key", key);
                            session.put("value", value);
                            return session;
                        }))
                .collectList()
                .map(sessions -> {
                    Map<String, Object> data = new HashMap<>();
                    data.put("sessions", sessions);
                    data.put("limit", limit);
                    data.put("total", sessions.size());
                    return HttpResponseUtils.ok(data);
                });
    }

    /**
     * 将UserSessionEntity转换为Map
     */
    private Map<String, Object> convertSessionToMap(UserSessionEntity session) {
        Map<String, Object> sessionData = new HashMap<>();
        sessionData.put("userId", session.getUserId());
        sessionData.put("currentState", session.getCurrentState());
        sessionData.put("referenceMessageId", session.getReferenceMessageId());
        
        // 计算会话持续时间
        long currentTime = System.currentTimeMillis();
        long minutesAgo = (currentTime - session.getTimestamp()) / (60 * 1000);
        sessionData.put("timestamp", session.getTimestamp());
        sessionData.put("minutesAgo", minutesAgo);
        sessionData.put("isActive", minutesAgo < 30); // 假设30分钟内的会话为活跃
        
        return sessionData;
    }

}