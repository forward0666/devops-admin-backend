package com.backend.bot.controller;

import com.backend.bot.service.GroupMessageCleanupService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;

import java.util.HashMap;
import java.util.Map;

/**
 * 群消息清理统计控制器
 * 
 * 提供群消息清理功能的统计信息查询接口，用于监控系统运行状态。
 */
@RestController
@RequestMapping("/groupMessageCleanUp")
@RequiredArgsConstructor
@Slf4j
public class GroupMessageCleanupController {

    private final GroupMessageCleanupService groupMessageCleanupService;

    /**
     * 获取群消息清理统计信息
     * 
     * @return 包含清理统计信息的 Map
     */
    @GetMapping("/stats")
    public Mono<Map<String, Object>> getCleanupStats() {
        return Mono.just(createStatsResponse());
    }

    /**
     * 创建统计响应对象
     * 
     * @return 包含统计信息的 Map
     */
    private Map<String, Object> createStatsResponse() {
        Map<String, Object> response = new HashMap<>();
        response.put("pendingCleanupCount", groupMessageCleanupService.getPendingCleanupCount());
        response.put("timestamp", System.currentTimeMillis());
        
        log.debug("Returning group message cleanup stats");
        return response;
    }
}