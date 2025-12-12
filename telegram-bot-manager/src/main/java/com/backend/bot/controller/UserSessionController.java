package com.backend.bot.controller;

import com.backend.bot.entity.UserSessionEntity;
import com.backend.bot.service.RedisUserSessionService;
import com.backend.bot.service.UserSessionService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
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
                    return ResponseEntity.ok(buildResponse(HttpStatus.OK, "查询成功", sessionData));
                })
                .defaultIfEmpty(ResponseEntity.ok(buildResponse(HttpStatus.OK, "用户无活跃会话", 
                        Map.of("userId", userId, "hasSession", false))));
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
                    return ResponseEntity.ok(buildResponse(HttpStatus.OK, "用户会话清除成功", data));
                });
    }

    /**
     * 获取会话统计信息
     * 
     * @return 会话统计信息
     */
    @GetMapping("/stats")
    public Mono<ResponseEntity<Map<String, Object>>> getSessionStats() {
        // 注意：这里需要实现一个获取所有活跃会话的方法
        // 暂时返回模拟数据
        Map<String, Object> stats = new HashMap<>();
        stats.put("totalSessions", 0);
        stats.put("activeSessions", 0);
        stats.put("expiredSessions", 0);
        stats.put("averageSessionDuration", "0 minutes");
        
        return Mono.just(ResponseEntity.ok(buildResponse(HttpStatus.OK, "统计信息获取成功", stats)));
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
        // 注意：这里需要实现一个获取所有活跃会话的方法
        // 暂时返回空列表
        return Mono.just(ResponseEntity.ok(buildResponse(HttpStatus.OK, "查询成功", 
                Map.of("sessions", new Object[0], "limit", limit))));
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

    /**
     * 构建统一响应结构
     */
    private Map<String, Object> buildResponse(HttpStatus status, String msg, Object data) {
        Map<String, Object> result = new HashMap<>();
        result.put("status", status.is2xxSuccessful() ? "ok" : "error");
        result.put("code", status.value());
        result.put("message", msg);
        if (data != null) {
            result.put("data", data);
        }
        return result;
    }
}