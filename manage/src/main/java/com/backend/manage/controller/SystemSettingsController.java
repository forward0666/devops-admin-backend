package com.backend.manage.controller;

import com.backend.manage.annotation.OperationLog;
import com.backend.manage.dto.ApiResponseDto;
import com.backend.manage.service.SystemSettingsService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * 系统设置控制器
 * 提供系统配置管理的REST API接口
 * 
 * 功能说明：
 * - 系统基本配置管理（系统设置、安全设置、密码策略等）
 * - IP白名单管理和访问控制配置
 * - 配置的导入导出功能
 * - Redis缓存管理
 * - 配置重置到默认值
 * - 所有操作都记录操作日志
 * 
 * 权限说明：
 * - 需要系统管理员权限才能访问和修改系统设置
 * - 使用@OperationLog注解自动记录操作日志
 * 
 * 技术特点：
 * - 使用Spring Boot RESTful API设计
 * - 集成Lombok简化代码
 * - 使用统一的ApiResponse响应格式
 * - 支持JSON格式的请求和响应
 * - 集成操作日志记录功能
 */
@Slf4j
@RestController
@RequestMapping("/settings")
@RequiredArgsConstructor // 自动生成包含 final 字段的构造函数
public class SystemSettingsController {

    private final SystemSettingsService systemSettingsService;

    /**
     * 获取所有系统设置
     * 读取系统基本配置信息
     * 
     * 功能说明：
     * - 获取系统全局配置参数
     * - 包括系统名称、版本、时区、语言等基础设置
     * - 不记录响应内容到操作日志（避免敏感信息泄露）
     * - 需要管理员权限访问
     * 
     * 返回格式：包含所有系统设置的键值对Map
     * 权限要求：系统管理员
     * 日志记录：记录操作但不记录响应内容
     */
    @GetMapping("/system")
    @OperationLog(
            operationType = "SETTINGS_READ",
            operationName = "获取系统设置",
            resourceType = "SYSTEM_SETTINGS",
            description = "查看系统基本配置信息",
            logResponse = false
    )
    public ResponseEntity<ApiResponseDto<Map<String, Object>>> getSystemSettings() {
        log.info("GET /settings/system - Fetching system settings");
        Map<String, Object> settings = systemSettingsService.getSystemSettings();
        return ResponseEntity.ok(ApiResponseDto.success("System settings retrieved successfully", settings));
    }

    /**
     * 更新系统设置
     * 修改系统基本配置信息
     * 
     * 功能说明：
     * - 批量更新系统全局配置参数
     * - 支持部分更新，只修改传入的参数
     * - 验证参数合法性
     * - 自动刷新相关缓存
     * - 需要管理员权限访问
     * 
     * 参数说明：
     * @param settings 包含要更新的设置键值对的Map
     * 
     * 返回格式：更新后的系统设置Map
     * 权限要求：系统管理员
     * 日志记录：记录操作和请求内容
     */
    @PutMapping("/system")
    @OperationLog(
            operationType = "SETTINGS_UPDATE",
            operationName = "更新系统设置",
            resourceType = "SYSTEM_SETTINGS",
            description = "修改系统基本配置信息"
            //            ,
            // logRequest = true,
            // logResponse = true
    )
    public ResponseEntity<ApiResponseDto<Map<String, Object>>> updateSystemSettings(@RequestBody Map<String, Object> settings) {
        log.info("PUT /settings/system - Updating system settings");
        Map<String, Object> updatedSettings = systemSettingsService.updateSystemSettings(settings);
        return ResponseEntity.ok(ApiResponseDto.success("System settings updated successfully", updatedSettings));
    }

    /**
     * 获取安全设置
     * 读取系统安全相关配置信息
     * 
     * 功能说明：
     * - 获取系统安全配置参数
     * - 包括登录安全、访问控制、审计日志等设置
     * - 不记录响应内容到操作日志（安全考虑）
     * - 需要管理员权限访问
     * 
     * 返回格式：包含安全设置的键值对Map
     * 权限要求：系统管理员
     * 日志记录：记录操作但不记录响应内容
     */
    @GetMapping("/security")
    @OperationLog(
            operationType = "SECURITY_SETTINGS_READ",
            operationName = "获取安全设置",
            resourceType = "SECURITY_SETTINGS",
            description = "查看系统安全配置信息",
            logResponse = false
    )
    public ResponseEntity<ApiResponseDto<Map<String, Object>>> getSecuritySettings() {
        log.info("GET /settings/security - Fetching security settings");
        Map<String, Object> settings = systemSettingsService.getSecuritySettings();
        return ResponseEntity.ok(ApiResponseDto.success("Security settings retrieved successfully", settings));
    }

    /**
     * 更新安全设置
     * 修改系统安全相关配置信息
     * 
     * 功能说明：
     * - 批量更新系统安全配置参数
     * - 包括密码策略、登录限制、会话管理等
     * - 验证安全参数合法性
     * - 自动刷新安全相关缓存
     * - 需要管理员权限访问
     * 
     * 参数说明：
     * @param settings 包含要更新的安全设置键值对的Map
     * 
     * 返回格式：更新后的安全设置Map
     * 权限要求：系统管理员
     * 日志记录：记录操作和请求内容
     */
    @PutMapping("/security")
    @OperationLog(
            operationType = "SECURITY_SETTINGS_UPDATE",
            operationName = "更新安全设置",
            resourceType = "SECURITY_SETTINGS",
            description = "修改系统安全配置信息"
            //            ,
            // logRequest = true,
            // logResponse = true
    )
    public ResponseEntity<ApiResponseDto<Map<String, Object>>> updateSecuritySettings(@RequestBody Map<String, Object> settings) {
        log.info("PUT /settings/security - Updating security settings");
        Map<String, Object> updatedSettings = systemSettingsService.updateSecuritySettings(settings);
        return ResponseEntity.ok(ApiResponseDto.success("Security settings updated successfully", updatedSettings));
    }

    /**
     * 获取密码策略设置
     * 读取系统密码复杂度要求配置
     * 
     * 功能说明：
     * - 获取密码复杂度策略配置
     * - 包括最小长度、特殊字符要求、数字要求等
     * - 密码历史记录策略
     * - 密码过期时间设置
     * - 需要管理员权限访问
     * 
     * 返回格式：包含密码策略设置的键值对Map
     * 权限要求：系统管理员
     * 日志记录：记录操作但不记录响应内容（安全考虑）
     */
    @GetMapping("/security/password-policy")
    @OperationLog(
            operationType = "PASSWORD_POLICY_READ",
            operationName = "获取密码策略",
            resourceType = "PASSWORD_POLICY",
            description = "查看密码复杂度和安全策略配置",
            logResponse = false
    )
    public ResponseEntity<ApiResponseDto<Map<String, Object>>> getPasswordPolicy() {
        log.info("GET /settings/security/password-policy - Fetching password policy");
        Map<String, Object> policy = systemSettingsService.getPasswordPolicy();
        return ResponseEntity.ok(ApiResponseDto.success("Password policy retrieved successfully", policy));
    }

    /**
     * 更新密码策略设置
     * 修改系统密码复杂度要求配置
     * 
     * 功能说明：
     * - 更新密码复杂度策略配置
     * - 设置密码最小长度、特殊字符要求、数字要求等
     * - 配置密码历史记录策略
     * - 设置密码过期时间
     * - 需要管理员权限访问
     * 
     * 参数说明：
     * @param policy 包含要更新的密码策略键值对的Map
     * 
     * 返回格式：更新后的密码策略设置Map
     * 权限要求：系统管理员
     * 日志记录：记录操作和请求内容
     */
    @PutMapping("/security/password-policy")
    @OperationLog(
            operationType = "PASSWORD_POLICY_UPDATE",
            operationName = "更新密码策略",
            resourceType = "PASSWORD_POLICY",
            description = "修改密码复杂度和安全策略配置"
            //,
            // logRequest = true,
            // logResponse = true
    )
    public ResponseEntity<ApiResponseDto<Map<String, Object>>> updatePasswordPolicy(@RequestBody Map<String, Object> policy) {
        log.info("PUT /settings/security/password-policy - Updating password policy");
        Map<String, Object> updatedPolicy = systemSettingsService.updatePasswordPolicy(policy);
        return ResponseEntity.ok(ApiResponseDto.success("Password policy updated successfully", updatedPolicy));
    }

    /**
     * 获取登录安全设置
     * 读取系统登录安全相关配置
     * 
     * 功能说明：
     * - 获取登录安全策略配置
     * - 包括登录失败锁定策略、会话超时设置
     * - 多因素认证配置
     * - 登录尝试限制设置
     * - 需要管理员权限访问
     * 
     * 返回格式：包含登录安全设置的键值对Map
     * 权限要求：系统管理员
     * 日志记录：记录操作但不记录响应内容（安全考虑）
     */
    @GetMapping("/security/login")
    @OperationLog(
            operationType = "LOGIN_SECURITY_READ",
            operationName = "获取登录安全设置",
            resourceType = "LOGIN_SECURITY",
            description = "查看登录安全策略和限制配置",
            logResponse = false
    )
    public ResponseEntity<ApiResponseDto<Map<String, Object>>> getLoginSecuritySettings() {
        log.info("GET /settings/security/login - Fetching login security settings");
        Map<String, Object> settings = systemSettingsService.getLoginSecuritySettings();
        return ResponseEntity.ok(ApiResponseDto.success("Login security settings retrieved successfully", settings));
    }

    /**
     * 更新登录安全设置
     * 修改系统登录安全相关配置
     * 
     * 功能说明：
     * - 更新登录安全策略配置
     * - 设置登录失败锁定策略、会话超时时间
     * - 配置多因素认证设置
     * - 设置登录尝试限制
     * - 需要管理员权限访问
     * 
     * 参数说明：
     * @param settings 包含要更新的登录安全设置键值对的Map
     * 
     * 返回格式：更新后的登录安全设置Map
     * 权限要求：系统管理员
     * 日志记录：记录操作和请求内容
     */
    @PutMapping("/security/login")
    @OperationLog(
            operationType = "LOGIN_SECURITY_UPDATE",
            operationName = "更新登录安全设置",
            resourceType = "LOGIN_SECURITY",
            description = "修改登录安全策略和限制配置"
            // ,
            // logRequest = true,
            // logResponse = true
    )
    public ResponseEntity<ApiResponseDto<Map<String, Object>>> updateLoginSecuritySettings(@RequestBody Map<String, Object> settings) {
        log.info("PUT /settings/security/login - Updating login security settings");
        Map<String, Object> updatedSettings = systemSettingsService.updateLoginSecuritySettings(settings);
        return ResponseEntity.ok(ApiResponseDto.success("Login security settings updated successfully", updatedSettings));
    }

    /**
     * 获取IP访问控制设置（IP白名单）
     * 读取系统IP访问控制配置
     * 
     * 功能说明：
     * - 获取IP白名单配置信息
     * - 包括允许访问的IP地址列表
     * - CIDR格式的IP范围支持
     * - 访问控制策略配置
     * - 需要管理员权限访问
     * 
     * 返回格式：包含IP访问控制设置的键值对Map
     * 权限要求：系统管理员
     * 日志记录：记录操作但不记录响应内容（安全考虑）
     */
    @GetMapping("/security/ip-control")
    @OperationLog(
            operationType = "IP_WHITELIST_READ",
            operationName = "获取IP白名单设置",
            resourceType = "IP_WHITELIST",
            description = "查看IP访问控制和白名单配置",
            logResponse = false
    )
    public ResponseEntity<ApiResponseDto<Map<String, Object>>> getIPAccessControl() {
        log.info("GET /settings/security/ip-control - Fetching IP access control settings");
        Map<String, Object> settings = systemSettingsService.getIPAccessControl();
        return ResponseEntity.ok(ApiResponseDto.success("IP access control settings retrieved successfully", settings));
    }

    /**
     * 更新IP访问控制设置（IP白名单）
     * 修改系统IP访问控制配置
     * 
     * 功能说明：
     * - 更新IP白名单配置
     * - 添加、删除、修改允许访问的IP地址
     * - 支持CIDR格式的IP范围
     * - 配置访问控制策略
     * - 需要管理员权限访问
     * 
     * 参数说明：
     * @param settings 包含要更新的IP访问控制设置键值对的Map
     * 
     * 返回格式：更新后的IP访问控制设置Map
     * 权限要求：系统管理员
     * 日志记录：记录操作和请求内容
     */
    @PutMapping("/security/ip-control")
    @OperationLog(
            operationType = "IP_WHITELIST_UPDATE",
            operationName = "更新IP白名单设置",
            resourceType = "IP_WHITELIST",
            description = "修改IP访问控制和白名单配置，包括添加、删除、修改白名单IP地址"
            // ,
            // logRequest = true,
            // logResponse = true
    )
    public ResponseEntity<ApiResponseDto<Map<String, Object>>> updateIPAccessControl(@RequestBody Map<String, Object> settings) {
        log.info("PUT /settings/security/ip-control - Updating IP access control settings");
        Map<String, Object> updatedSettings = systemSettingsService.updateIPAccessControl(settings);
        return ResponseEntity.ok(ApiResponseDto.success("IP access control settings updated successfully", updatedSettings));
    }

    /**
     * 重置设置为默认值
     * 将指定类别的系统设置恢复为默认配置
     * 
     * 功能说明：
     * - 将指定类别的设置重置为系统默认值
     * - 支持按类别重置（系统设置、安全设置、密码策略等）
     * - 清除自定义配置，恢复出厂设置
     * - 需要管理员权限访问
     * 
     * 参数说明：
     * @param category 要重置的设置类别（如：system、security、password-policy等）
     * 
     * 返回格式：操作成功消息
     * 权限要求：系统管理员
     * 日志记录：记录操作但不记录响应内容
     */
    @PostMapping("/reset")
    @OperationLog(
            operationType = "SETTINGS_RESET",
            operationName = "重置设置为默认值",
            resourceType = "SYSTEM_SETTINGS",
            description = "将指定类别的设置重置为系统默认值",
//            logRequest = true,
            logResponse = false
    )
    public ResponseEntity<ApiResponseDto<Void>> resetToDefaults(@RequestParam String category) {
        log.info("POST /settings/reset?category={} - Resetting settings to defaults", category);
        systemSettingsService.resetToDefaults(category);
        return ResponseEntity.ok(ApiResponseDto.success("Settings reset to defaults successfully", null));
    }

    /**
     * 导出系统设置配置
     * 导出所有系统配置信息到外部文件
     * 
     * 功能说明：
     * - 导出所有系统配置信息
     * - 包括系统设置、安全设置、密码策略、IP白名单等
     * - 生成JSON格式的配置文件
     * - 用于备份或迁移配置
     * - 需要管理员权限访问
     * 
     * 返回格式：包含所有系统设置的键值对Map
     * 权限要求：系统管理员
     * 日志记录：记录操作但不记录请求和响应内容（安全考虑）
     */
    @GetMapping("/export")
    @OperationLog(
            operationType = "SETTINGS_EXPORT",
            operationName = "导出系统设置",
            resourceType = "SYSTEM_SETTINGS",
            description = "导出所有系统配置信息，包括安全设置、密码策略、IP白名单等",
            logRequest = false,
            logResponse = false
    )
    public ResponseEntity<ApiResponseDto<Map<String, Object>>> exportSettings() {
        log.info("GET /settings/export - Exporting all settings");
        Map<String, Object> allSettings = systemSettingsService.exportAllSettings();
        return ResponseEntity.ok(ApiResponseDto.success("Settings exported successfully", allSettings));
    }

    /**
     * 导入系统设置配置
     * 从外部文件导入系统配置信息
     * 
     * 功能说明：
     * - 导入系统配置信息
     * - 支持从JSON格式的配置文件导入
     * - 可能覆盖现有配置
     * - 用于恢复备份或应用标准配置
     * - 需要管理员权限访问
     * 
     * 参数说明：
     * @param settings 包含要导入的系统设置键值对的Map
     * 
     * 返回格式：操作成功消息
     * 权限要求：系统管理员
     * 日志记录：记录操作但不记录响应内容
     */
    @PostMapping("/import")
    @OperationLog(
            operationType = "SETTINGS_IMPORT",
            operationName = "导入系统设置",
            resourceType = "SYSTEM_SETTINGS",
            description = "导入系统配置信息，包括安全设置、密码策略、IP白名单等，可能覆盖现有配置",
//            logRequest = true,
            logResponse = false
    )
    public ResponseEntity<ApiResponseDto<Void>> importSettings(@RequestBody Map<String, Object> settings) {
        log.info("POST /settings/import - Importing settings configuration");
        systemSettingsService.importSettings(settings);
        return ResponseEntity.ok(ApiResponseDto.success("Settings imported successfully", null));
    }

    /**
     * 添加IP到白名单
     * 向系统IP白名单中添加新的IP地址
     * 
     * 功能说明：
     * - 向IP白名单中添加新的IP地址或IP段
     * - 支持单个IP和CIDR格式的IP范围
     * - 验证IP地址格式合法性
     * - 需要管理员权限访问
     * 
     * 参数说明：
     * @param request 包含要添加的IP信息的Map（如：{"ip": "192.168.1.1", "description": "内部网络"}）
     * 
     * 返回格式：更新后的IP白名单设置Map
     * 权限要求：系统管理员
     * 日志记录：记录操作和请求内容
     */
    @PostMapping("/security/ip-control/whitelist/add")
    @OperationLog(
            operationType = "IP_WHITELIST_ADD",
            operationName = "添加IP白名单",
            resourceType = "IP_WHITELIST",
            description = "向IP白名单中添加新的IP地址或IP段"
            // ,
            // logRequest = true,
            // logResponse = true
    )
    public ResponseEntity<ApiResponseDto<Map<String, Object>>> addIPToWhitelist(@RequestBody Map<String, Object> request) {
        log.info("POST /settings/security/ip-control/whitelist/add - Adding IP to whitelist");
        Map<String, Object> result = systemSettingsService.addIPToWhitelist(request);
        return ResponseEntity.ok(ApiResponseDto.success("IP added to whitelist successfully", result));
    }

    /**
     * 从白名单中移除IP
     * 从系统IP白名单中删除指定的IP地址
     * 
     * 功能说明：
     * - 从IP白名单中移除指定的IP地址或IP段
     * - 支持按IP地址或描述信息匹配删除
     * - 需要管理员权限访问
     * 
     * 参数说明：
     * @param request 包含要移除的IP信息的Map（如：{"ip": "192.168.1.1"} 或 {"description": "内部网络"}）
     * 
     * 返回格式：更新后的IP白名单设置Map
     * 权限要求：系统管理员
     * 日志记录：记录操作和请求内容
     */
    @DeleteMapping("/security/ip-control/whitelist/remove")
    @OperationLog(
            operationType = "IP_WHITELIST_REMOVE",
            operationName = "移除IP白名单",
            resourceType = "IP_WHITELIST",
            description = "从IP白名单中移除指定的IP地址或IP段"
            // ,
            // logRequest = true,
            // logResponse = true
    )
    public ResponseEntity<ApiResponseDto<Map<String, Object>>> removeIPFromWhitelist(@RequestBody Map<String, Object> request) {
        log.info("DELETE /settings/security/ip-control/whitelist/remove - Removing IP from whitelist");
        Map<String, Object> result = systemSettingsService.removeIPFromWhitelist(request);
        return ResponseEntity.ok(ApiResponseDto.success("IP removed from whitelist successfully", result));
    }

    /**
     * 批量更新IP白名单
     * 批量操作IP白名单配置
     * 
     * 功能说明：
     * - 批量添加、删除、修改IP白名单
     * - 支持一次操作多个IP地址
     * - 提高配置效率
     * - 需要管理员权限访问
     * 
     * 参数说明：
     * @param request 包含批量操作信息的Map（如：{"add": [...], "remove": [...], "update": [...]}）
     * 
     * 返回格式：更新后的IP白名单设置Map
     * 权限要求：系统管理员
     * 日志记录：记录操作和请求内容
     */
    @PutMapping("/security/ip-control/whitelist/bulk")
    @OperationLog(
            operationType = "IP_WHITELIST_BULK_UPDATE",
            operationName = "批量更新IP白名单",
            resourceType = "IP_WHITELIST",
            description = "批量更新IP白名单，包括添加、删除、修改多个IP地址或IP段"
            // ,
            // logRequest = true,
            // logResponse = true
    )
    public ResponseEntity<ApiResponseDto<Map<String, Object>>> bulkUpdateIPWhitelist(@RequestBody Map<String, Object> request) {
        log.info("PUT /settings/security/ip-control/whitelist/bulk - Bulk updating IP whitelist");
        Map<String, Object> result = systemSettingsService.bulkUpdateIPWhitelist(request);
        return ResponseEntity.ok(ApiResponseDto.success("IP whitelist bulk updated successfully", result));
    }

    /**
     * 清空IP白名单
     * 删除所有IP白名单配置
     * 
     * 功能说明：
     * - 清空所有IP白名单配置
     * - 这是一个高风险操作，会移除所有访问限制
     * - 系统将允许任何IP地址访问
     * - 需要管理员权限访问
     * 
     * 返回格式：操作成功消息
     * 权限要求：系统管理员
     * 日志记录：记录操作但不记录请求内容（安全考虑）
     */
    @DeleteMapping("/security/ip-control/whitelist/clear")
    @OperationLog(
            operationType = "IP_WHITELIST_CLEAR",
            operationName = "清空IP白名单",
            resourceType = "IP_WHITELIST",
            description = "清空所有IP白名单配置，这是一个高风险操作",
            logRequest = false
            //            ,
            //            logResponse = true
    )
    public ResponseEntity<ApiResponseDto<Void>> clearIPWhitelist() {
        log.info("DELETE /settings/security/ip-control/whitelist/clear - Clearing IP whitelist");
        systemSettingsService.clearIPWhitelist();
        return ResponseEntity.ok(ApiResponseDto.success("IP whitelist cleared successfully", null));
    }

    /**
     * 清理Redis缓存
     * 清除系统设置相关的Redis缓存
     * 
     * 功能说明：
     * - 清理系统设置相关的Redis缓存
     * - 强制从数据库重新加载配置
     * - 解决配置更新后缓存未刷新的问题
     * - 需要管理员权限访问
     * 
     * 返回格式：缓存清理结果Map
     * 权限要求：系统管理员
     * 日志记录：记录操作但不记录请求内容
     */
    @PostMapping("/cache/clear")
    @OperationLog(
            operationType = "CACHE_CLEAR",
            operationName = "清理Redis缓存",
            resourceType = "SYSTEM_SETTINGS",
            description = "清理系统设置相关的Redis缓存，强制从数据库重新加载配置",
            logRequest = false
//            ,
//            logResponse = true
    )
    public ResponseEntity<ApiResponseDto<Map<String, Object>>> clearRedisCache() {
        log.info("POST /settings/cache/clear - Clearing Redis cache for system settings");
        Map<String, Object> result = systemSettingsService.clearRedisCache();
        return ResponseEntity.ok(ApiResponseDto.success("Redis cache cleared successfully", result));
    }
}
