package com.admin.manage.service;

import com.admin.manage.repository.SystemConfigRepository;
import com.fasterxml.jackson.core.type.TypeReference;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.*;

@Slf4j
@Service
public class SystemSettingsService {

    @Autowired
    private SystemConfigRepository systemConfigRepository;

    @Autowired
    private CacheService cacheService;

    // ---------------- Password Policy ----------------

    public Map<String, Object> updatePasswordPolicy(Map<String, Object> policy) {
        log.info("Updating password policy");
        try {
            if (policy.containsKey("passwordComplexity")) {
                systemConfigRepository.setConfigValue("security.password.complexity", policy.get("passwordComplexity").toString());
            }
            if (policy.containsKey("minPasswordLength")) {
                systemConfigRepository.setConfigValue("security.password.min_length", policy.get("minPasswordLength").toString());
            }
            if (policy.containsKey("passwordExpirationDays")) {
                systemConfigRepository.setConfigValue("security.password.expiration_days", policy.get("passwordExpirationDays").toString());
            }

            cacheService.clearSystemSettingsCache("password-policy");
            return getPasswordPolicy();
        } catch (Exception e) {
            log.error("Error updating password policy", e);
            throw new RuntimeException("Failed to update password policy", e);
        }
    }

    @SuppressWarnings("unchecked")
    public Map<String, Object> getPasswordPolicy() {
        log.info("Fetching password policy");
        Object cached = cacheService.getCachedSystemSettings("password-policy");
        if (cached instanceof Map) return (Map<String, Object>) cached;

        Map<String, Object> policy = new HashMap<>();
        try {
            policy.put("passwordComplexity", systemConfigRepository.getConfigValue("security.password.complexity", "Medium"));
            policy.put("minPasswordLength", Integer.parseInt(systemConfigRepository.getConfigValue("security.password.min_length", "8")));
            policy.put("passwordExpirationDays", Integer.parseInt(systemConfigRepository.getConfigValue("security.password.expiration_days", "90")));

            cacheService.cacheSystemSettings("password-policy", policy);
            return policy;
        } catch (Exception e) {
            log.error("Error retrieving password policy", e);
            throw new RuntimeException("Failed to retrieve password policy", e);
        }
    }

    // ---------------- System Settings ----------------

    @SuppressWarnings("unchecked")
    public Map<String, Object> getSystemSettings() {
        log.info("Fetching system settings");
        Object cached = cacheService.getCachedSystemSettings("system");
        if (cached instanceof Map) return (Map<String, Object>) cached;

        Map<String, Object> settings = new HashMap<>();
        try {
            settings.put("systemName", systemConfigRepository.getConfigValue("system.name", "DevOps Admin Platform"));
            settings.put("maintenanceMode", Boolean.parseBoolean(systemConfigRepository.getConfigValue("system.maintenance_mode", "false")));
            settings.put("language", systemConfigRepository.getConfigValue("system.language", "English"));
            settings.put("logLevel", systemConfigRepository.getConfigValue("system.log_level", "Info"));
            settings.put("maxConcurrentUsers", Integer.parseInt(systemConfigRepository.getConfigValue("system.max_concurrent_users", "100")));

            cacheService.cacheSystemSettings("system", settings);
            return settings;
        } catch (Exception e) {
            log.error("Error retrieving system settings", e);
            throw new RuntimeException("Failed to retrieve system settings", e);
        }
    }

    public Map<String, Object> updateSystemSettings(Map<String, Object> settings) {
        log.info("Updating system settings");
        try {
            if (settings.containsKey("systemName")) {
                systemConfigRepository.setConfigValue("system.name", settings.get("systemName").toString());
            }
            if (settings.containsKey("maintenanceMode")) {
                systemConfigRepository.setConfigValue("system.maintenance_mode", settings.get("maintenanceMode").toString());
            }
            if (settings.containsKey("language")) {
                systemConfigRepository.setConfigValue("system.language", settings.get("language").toString());
            }
            if (settings.containsKey("logLevel")) {
                systemConfigRepository.setConfigValue("system.log_level", settings.get("logLevel").toString());
            }
            if (settings.containsKey("maxConcurrentUsers")) {
                systemConfigRepository.setConfigValue("system.max_concurrent_users", settings.get("maxConcurrentUsers").toString());
            }

            cacheService.clearSystemSettingsCache("system");
            return getSystemSettings();
        } catch (Exception e) {
            log.error("Error updating system settings", e);
            throw new RuntimeException("Failed to update system settings", e);
        }
    }

    // ---------------- Security Settings ----------------

    @SuppressWarnings("unchecked")
    public Map<String, Object> getSecuritySettings() {
        log.info("Fetching security settings");
        Object cached = cacheService.getCachedSystemSettings("security");
        if (cached instanceof Map) return (Map<String, Object>) cached;

        Map<String, Object> settings = new HashMap<>();
        try {
            settings.put("passwordPolicy", getPasswordPolicy());
            settings.put("loginSecurity", getLoginSecuritySettings());
            Map<String, Object> ipAccessControl = getIPAccessControl();
            log.info("IP Access Control settings: {}", ipAccessControl); // ✅ 打印出来
            settings.put("ipAccessControl", getIPAccessControl());
            cacheService.cacheSystemSettings("security", settings);
            return settings;
        } catch (Exception e) {
            log.error("Error retrieving security settings", e);
            throw new RuntimeException("Failed to retrieve security settings", e);
        }
    }

    public Map<String, Object> updateSecuritySettings(Map<String, Object> settings) {
        log.info("Updating security settings");
        try {
            if (settings.containsKey("passwordPolicy")) updatePasswordPolicy((Map<String, Object>) settings.get("passwordPolicy"));
            if (settings.containsKey("loginSecurity")) updateLoginSecuritySettings((Map<String, Object>) settings.get("loginSecurity"));
            if (settings.containsKey("ipAccessControl")) updateIPAccessControl((Map<String, Object>) settings.get("ipAccessControl"));

            cacheService.clearSystemSettingsCache("security");
            return getSecuritySettings();
        } catch (Exception e) {
            log.error("Error updating security settings", e);
            throw new RuntimeException("Failed to update security settings", e);
        }
    }

    // ---------------- Login Security ----------------

    public Map<String, Object> getLoginSecuritySettings() {
        Map<String, Object> loginSecurity = new HashMap<>();
        loginSecurity.put("maxLoginAttempts", Integer.parseInt(systemConfigRepository.getConfigValue("security.login.max_attempts", "5")));
        loginSecurity.put("accountLockDuration", Integer.parseInt(systemConfigRepository.getConfigValue("security.login.lock_duration", "30")));
        loginSecurity.put("twoFactorAuth", Boolean.parseBoolean(systemConfigRepository.getConfigValue("security.login.two_factor_auth", "false")));
        return loginSecurity;
    }

    public Map<String, Object> updateLoginSecuritySettings(Map<String, Object> settings) {
        if (settings.containsKey("maxLoginAttempts")) {
            systemConfigRepository.setConfigValue("security.login.max_attempts", settings.get("maxLoginAttempts").toString());
        }
        if (settings.containsKey("accountLockDuration")) {
            systemConfigRepository.setConfigValue("security.login.lock_duration", settings.get("accountLockDuration").toString());
        }
        if (settings.containsKey("twoFactorAuth")) {
            systemConfigRepository.setConfigValue("security.login.two_factor_auth", settings.get("twoFactorAuth").toString());
        }
        cacheService.clearSystemSettingsCache("login-security");
        return getLoginSecuritySettings();
    }

    // ---------------- IP Access Control ----------------

    @SuppressWarnings("unchecked")
    public Map<String, Object> getIPAccessControl() {
        log.info("Fetching IP access control");
        Object cached = cacheService.getCachedSystemSettings("ip-control");
        if (cached instanceof Map) return (Map<String, Object>) cached;

        Map<String, Object> settings = new HashMap<>();
        settings.put("allowedIPs", systemConfigRepository.getConfigObject(
                "security.ip.allowed_ips",
                new TypeReference<List<String>>() {},
                new ArrayList<>()
        ));
        settings.put("blockedIPs", systemConfigRepository.getConfigObject(
                "security.ip.blocked_ips",
                new TypeReference<List<String>>() {},
                new ArrayList<>()
        ));

        cacheService.cacheSystemSettings("ip-control", settings);
        return settings;
    }

//    public Map<String, Object> updateIPAccessControl(Map<String, Object> settings) {
//        if (settings.containsKey("allowedIPs")) {
//            systemConfigRepository.setConfigObject("security.ip.allowed_ips", settings.get("allowedIPs"));
//        }
//        if (settings.containsKey("blockedIPs")) {
//            systemConfigRepository.setConfigObject("security.ip.blocked_ips", settings.get("blockedIPs"));
//        }
//        cacheService.clearSystemSettingsCache("ip-control");
//        return getIPAccessControl();
//    }
public Map<String, Object> updateIPAccessControl(Map<String, Object> settings) {
    // 1. 更新单独配置
    if (settings.containsKey("allowedIPs")) {
        systemConfigRepository.setConfigObject("security.ip.allowed_ips", settings.get("allowedIPs"));
    }
    if (settings.containsKey("blockedIPs")) {
        systemConfigRepository.setConfigObject("security.ip.blocked_ips", settings.get("blockedIPs"));
    }

    // 2. 清理单独缓存
    cacheService.clearSystemSettingsCache("ip-control");

    // 3. 同步更新 security 大对象里的 ipAccessControl
    Map<String, Object> securitySettings = getSecuritySettings(); // 先取出完整的 security 对象
    Map<String, Object> ipAccessControl = new HashMap<>();
    ipAccessControl.put("allowedIPs", settings.getOrDefault("allowedIPs", new ArrayList<>()));
    ipAccessControl.put("blockedIPs", settings.getOrDefault("blockedIPs", new ArrayList<>()));
    securitySettings.put("ipAccessControl", ipAccessControl);

    // 4. 覆盖写入缓存（保证 GET /manage/settings/security 返回的是最新的）
    cacheService.cacheSystemSettings("security", securitySettings);

    // 5. 返回最新的 ip-control
    return getIPAccessControl();
}


    public Map<String, Object> addIPToWhitelist(Map<String, Object> req) {
        List<String> current = systemConfigRepository.getConfigObject(
                "security.ip.allowed_ips",
                new TypeReference<List<String>>() {},
                new ArrayList<>()
        );
        String ip = req.get("ip").toString();
        if (!current.contains(ip)) current.add(ip);
        systemConfigRepository.setConfigObject("security.ip.allowed_ips", current);
        cacheService.clearSystemSettingsCache("ip-control");
        return getIPAccessControl();
    }

    public Map<String, Object> removeIPFromWhitelist(Map<String, Object> req) {
        List<String> current = systemConfigRepository.getConfigObject(
                "security.ip.allowed_ips",
                new TypeReference<List<String>>() {},
                new ArrayList<>()
        );
        current.remove(req.get("ip").toString());
        systemConfigRepository.setConfigObject("security.ip.allowed_ips", current);
        cacheService.clearSystemSettingsCache("ip-control");
        return getIPAccessControl();
    }

    public Map<String, Object> bulkUpdateIPWhitelist(Map<String, Object> req) {
        Object ips = req.get("allowedIPs");
        if (ips instanceof List) {
            systemConfigRepository.setConfigObject("security.ip.allowed_ips", ips);
        }
        cacheService.clearSystemSettingsCache("ip-control");
        return getIPAccessControl();
    }

    public void clearIPWhitelist() {
        systemConfigRepository.setConfigObject("security.ip.allowed_ips", new ArrayList<>());
        systemConfigRepository.setConfigObject("security.ip.blocked_ips", new ArrayList<>());
        cacheService.clearSystemSettingsCache("ip-control");
    }

    // ---------------- Reset / Import / Export ----------------

    public Map<String, Object> resetToDefaults(String category) {
        log.info("Resetting {} settings to defaults", category);
        if ("system".equals(category)) return getSystemSettings();
        if ("password-policy".equals(category)) return getPasswordPolicy();
        if ("login-security".equals(category)) return getLoginSecuritySettings();
        if ("ip-control".equals(category)) return getIPAccessControl();
        if ("security".equals(category)) return getSecuritySettings();
        throw new IllegalArgumentException("Unknown category: " + category);
    }

    public Map<String, Object> exportAllSettings() {
        Map<String, Object> all = new HashMap<>();
        all.put("system", getSystemSettings());
        all.put("passwordPolicy", getPasswordPolicy());
        all.put("loginSecurity", getLoginSecuritySettings());
        all.put("ipAccessControl", getIPAccessControl());
        return all;
    }

    public Map<String, Object> importSettings(Map<String, Object> settings) {
        if (settings.containsKey("system")) updateSystemSettings((Map<String, Object>) settings.get("system"));
        if (settings.containsKey("passwordPolicy")) updatePasswordPolicy((Map<String, Object>) settings.get("passwordPolicy"));
        if (settings.containsKey("loginSecurity")) updateLoginSecuritySettings((Map<String, Object>) settings.get("loginSecurity"));
        if (settings.containsKey("ipAccessControl")) updateIPAccessControl((Map<String, Object>) settings.get("ipAccessControl"));
        return exportAllSettings();
    }

    // ---------------- Cache Clearing ----------------

    public Map<String, Object> clearRedisCache() {
        cacheService.clearSystemSettingsCache("system");
        cacheService.clearSystemSettingsCache("security");
        cacheService.clearSystemSettingsCache("password-policy");
        cacheService.clearSystemSettingsCache("login-security");
        cacheService.clearSystemSettingsCache("ip-control");

        Map<String, Object> result = new HashMap<>();
        result.put("clearedCaches", Arrays.asList("system", "security", "password-policy", "login-security", "ip-control"));
        result.put("clearedAt", LocalDateTime.now());
        return result;
    }
}
