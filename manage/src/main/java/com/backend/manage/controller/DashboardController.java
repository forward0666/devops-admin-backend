package com.backend.manage.controller;

import com.backend.manage.service.OperationLogService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;

/**
 * 仪表板控制器
 * 处理仪表板相关的数据展示和统计功能
 * 
 * 功能说明：
 * - 提供仪表板所需的数据接口
 * - 主要处理最近活动记录的获取
 * - 集成日志记录和异常处理
 * - 使用MongoDB存储操作日志数据
 * - 限制数据返回数量以保证性能
 */
@Slf4j
@RestController
@RequestMapping("/dashboard")
@RequiredArgsConstructor
public class DashboardController {
    
    private final OperationLogService operationLogService;
    
    /**
     * 获取最近活动记录接口
     * 
     * 功能说明：
     * - 从MongoDB数据库中获取最近的操作活动记录
     * - 支持通过limit参数控制返回记录数量
     * - 默认返回5条记录，最大限制为5条
     * - 使用GET方法，支持查询参数
     * - 集成详细的日志记录，包括请求参数和结果统计
     * - 统一的响应格式，包含状态码、消息和数据
     * - 使用try-catch处理异常，返回统一的错误信息
     * 
     * @param limit 返回记录的数量限制，默认为5，最大值为5
     * @return ResponseEntity包含操作结果，成功时返回活动记录数据，失败时返回错误信息
     */
    @GetMapping("/recent-activities")
    public ResponseEntity<Map<String, Object>> getRecentActivities(@RequestParam(defaultValue = "5") int limit) {
        try {
            log.info("Fetching recent activities from MongoDB, limit: {}", limit);
            
            // 限制最大数量为5
            if (limit > 5) {
                limit = 5;
            }
            
            var activities = operationLogService.getRecentOperationLogs(limit);
            
            Map<String, Object> response = new HashMap<>();
            response.put("code", 200);
            response.put("message", "获取最近活动记录成功");
            response.put("data", activities);
            
            log.info("Successfully fetched {} recent activities", activities.size());
            return ResponseEntity.ok(response);
            
        } catch (Exception e) {
            log.error("Failed to fetch recent activities: {}", e.getMessage(), e);
            Map<String, Object> errorResponse = new HashMap<>();
            errorResponse.put("code", 500);
            errorResponse.put("message", "获取最近活动记录失败: " + e.getMessage());
            return ResponseEntity.internalServerError().body(errorResponse);
        }
    }
}
