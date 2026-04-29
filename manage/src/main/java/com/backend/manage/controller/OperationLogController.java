package com.backend.manage.controller;

import com.backend.manage.entity.OperationLogEntity;
import com.backend.manage.service.OperationLogService;
import com.backend.manage.util.AccessValidator;
import com.backend.manage.util.JwtUtil;
import com.backend.manage.util.ResponseUtil;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import jakarta.servlet.http.HttpServletRequest;
import java.util.List;
import java.util.Map;

@Slf4j
@RestController
@RequestMapping("/operationLog")
@RequiredArgsConstructor
@Tag(name = "Operation Log Management", description = "操作日志管理接口")
public class OperationLogController {

    private final OperationLogService operationLogService;
    private final JwtUtil jwtUtil;

    @GetMapping
    @Operation(summary = "获取操作日志列表")
    public ResponseEntity<Map<String, Object>> getOperationLogs(
            HttpServletRequest request,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int pageSize,
            @RequestParam(defaultValue = "createdAt") String sortBy,
            @RequestParam(defaultValue = "desc") String sortDir,
            @RequestParam(required = false) String category,
            @RequestParam(required = false) String startDate,
            @RequestParam(required = false) String endDate) {

        AccessValidator.validate(request, jwtUtil, "sys_admin", "admin", "devops");
        int springDataPage = Math.max(0, page - 1);
        Page<OperationLogEntity> logs = operationLogService.getOperationLogs(springDataPage, pageSize, sortBy, sortDir, category, startDate, endDate);
        return ResponseUtil.page("获取操作日志成功", logs, page);
    }

    @GetMapping("/recent")
    @Operation(summary = "获取最近操作日志", description = "获取最近的操作日志（Dashboard专用，默认5条）")
    public ResponseEntity<Map<String, Object>> getRecentOperationLogs(
            HttpServletRequest request,
            @RequestParam(defaultValue = "5") int limit) {

        AccessValidator.validate(request, jwtUtil, "sys_admin", "admin", "devops");
        List<OperationLogEntity> logs = operationLogService.getRecentOperationLogs(limit);
        return ResponseUtil.success("获取最近操作日志成功", logs);
    }
}
