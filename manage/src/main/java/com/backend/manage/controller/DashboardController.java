package com.backend.manage.controller;

import com.backend.utils.dto.ApiResponseDto;
import com.backend.manage.entity.UserEntity;
import com.backend.manage.service.OperationLogService;
import com.backend.manage.service.DepartmentService;
import com.backend.manage.service.UserService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 仪表板控制器
 * 处理仪表板相关的数据展示和统计功能
 */
@Slf4j
@RestController
@RequestMapping("/dashboard")
@RequiredArgsConstructor
public class DashboardController {

    private final OperationLogService operationLogService;
    private final UserService userService;
    private final DepartmentService departmentService;

    @GetMapping("/stats")
    public ResponseEntity<ApiResponseDto<Map<String, Object>>> getStats() {
        log.info("Fetching dashboard stats");

        Map<String, Object> stats = new HashMap<>();
        List<UserEntity> allUsers = userService.getAllUsers();

        stats.put("totalUsers", allUsers.size());
        stats.put("activeUsers", allUsers.stream().filter(UserEntity::isActive).count());
        stats.put("totalDepartments", departmentService.getAllDepartments().size());
        Map<String, Long> roleDistribution = allUsers.stream()
            .collect(Collectors.groupingBy(UserEntity::getRole, Collectors.counting()));
        stats.put("roleDistribution", roleDistribution);

        return ResponseEntity.ok(ApiResponseDto.success("获取统计数据成功", stats));
    }

    @GetMapping("/recentActivities")
    public ResponseEntity<ApiResponseDto<List<?>>> getRecentActivities(@RequestParam(defaultValue = "5") int limit) {
        log.info("Fetching recent activities from MongoDB, limit: {}", limit);
        if (limit > 5) limit = 5;
        var activities = operationLogService.getRecentOperationLogs(limit);
        log.info("Successfully fetched {} recent activities", activities.size());
        return ResponseEntity.ok(ApiResponseDto.success("获取最近活动记录成功", activities));
    }
}