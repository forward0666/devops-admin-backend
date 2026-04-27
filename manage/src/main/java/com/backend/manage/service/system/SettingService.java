package com.backend.manage.service.system;

import com.backend.manage.mapper.system.SystemConfigMapper;
import com.backend.manage.entity.system.SystemConfigEntity;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.net.InetAddress;
import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class SettingService {

    private final SystemConfigMapper systemConfigMapper;
    private final com.backend.manage.service.CacheService cacheService;

    // IP Access Control
    private static final String ALLOWED_KEY = "setting.ip.allowed_ips";
    private static final String BLOCKED_KEY = "setting.ip.blocked_ips";

    // System Settings
    private static final String SYS_NAME_KEY = "setting.name";
    private static final String SYS_LOGO_KEY = "setting.logo";
    private static final String SYS_LANGUAGE_KEY = "setting.language";
    private static final String SYS_THEME_KEY = "setting.theme";

    // Password Policy
    private static final String SEC_PASSWORD_MIN_LEN = "setting.password.min_length";
    private static final String SEC_PASSWORD_REQUIRE_UPPER = "setting.password.require_uppercase";
    private static final String SEC_PASSWORD_REQUIRE_NUMBER = "setting.password.require_number";
    private static final String SEC_PASSWORD_REQUIRE_SPECIAL = "setting.password.require_special";
    private static final String SEC_PASSWORD_EXPIRE_DAYS = "setting.password.expire_days";

    // Login Security
    private static final String SEC_LOGIN_MAX_ATTEMPTS = "setting.login.max_attempts";
    private static final String SEC_LOGIN_LOCKOUT_MINUTES = "setting.login.lockout_minutes";
    private static final String SEC_LOGIN_CAPTCHA_ENABLED = "setting.login.captcha_enabled";

    // Session
    private static final String SESSION_TOKEN_EXPIRE = "session.token_expire_seconds";
    private static final String SESSION_REFRESH_EXPIRE = "session.refresh_token_expire_seconds";
    private static final String SESSION_MAX_CONCURRENT = "session.max_concurrent_sessions";

    // Default values
    private static final Map<String, String> DEFAULTS = new LinkedHashMap<>();
    static {
        DEFAULTS.put(SYS_NAME_KEY, "DevOps Admin");
        DEFAULTS.put(SYS_LOGO_KEY, "");
        DEFAULTS.put(SYS_LANGUAGE_KEY, "zh-CN");
        DEFAULTS.put(SYS_THEME_KEY, "light");
        DEFAULTS.put(SEC_PASSWORD_MIN_LEN, "8");
        DEFAULTS.put(SEC_PASSWORD_REQUIRE_UPPER, "true");
        DEFAULTS.put(SEC_PASSWORD_REQUIRE_NUMBER, "true");
        DEFAULTS.put(SEC_PASSWORD_REQUIRE_SPECIAL, "true");
        DEFAULTS.put(SEC_PASSWORD_EXPIRE_DAYS, "90");
        DEFAULTS.put(SEC_LOGIN_MAX_ATTEMPTS, "5");
        DEFAULTS.put(SEC_LOGIN_LOCKOUT_MINUTES, "30");
        DEFAULTS.put(SEC_LOGIN_CAPTCHA_ENABLED, "false");
        DEFAULTS.put(SESSION_TOKEN_EXPIRE, "86400");
        DEFAULTS.put(SESSION_REFRESH_EXPIRE, "604800");
        DEFAULTS.put(SESSION_MAX_CONCURRENT, "5");
    }

    private static final Map<String, String> KEY_DESCRIPTIONS = new LinkedHashMap<>();
    static {
        KEY_DESCRIPTIONS.put(SYS_NAME_KEY, "系统名称");
        KEY_DESCRIPTIONS.put(SYS_LOGO_KEY, "系统Logo URL");
        KEY_DESCRIPTIONS.put(SYS_LANGUAGE_KEY, "系统语言");
        KEY_DESCRIPTIONS.put(SYS_THEME_KEY, "系统主题");
        KEY_DESCRIPTIONS.put(SEC_PASSWORD_MIN_LEN, "密码最小长度");
        KEY_DESCRIPTIONS.put(SEC_PASSWORD_REQUIRE_UPPER, "密码要求大写字母");
        KEY_DESCRIPTIONS.put(SEC_PASSWORD_REQUIRE_NUMBER, "密码要求数字");
        KEY_DESCRIPTIONS.put(SEC_PASSWORD_REQUIRE_SPECIAL, "密码要求特殊字符");
        KEY_DESCRIPTIONS.put(SEC_PASSWORD_EXPIRE_DAYS, "密码过期天数");
        KEY_DESCRIPTIONS.put(SEC_LOGIN_MAX_ATTEMPTS, "最大登录尝试次数");
        KEY_DESCRIPTIONS.put(SEC_LOGIN_LOCKOUT_MINUTES, "登录锁定时间(分钟)");
        KEY_DESCRIPTIONS.put(SEC_LOGIN_CAPTCHA_ENABLED, "启用登录验证码");
        KEY_DESCRIPTIONS.put(ALLOWED_KEY, "IP白名单(逗号分隔)");
        KEY_DESCRIPTIONS.put(BLOCKED_KEY, "IP黑名单(逗号分隔)");
    }

    /**
     * 是否允许该 IP 访问
     */
    public boolean isIpAllowed(String clientIp) {
        log.info("Fetching IP access control");

        List<String> allowedList = loadConfig(ALLOWED_KEY);
        List<String> blockedList = loadConfig(BLOCKED_KEY);

        if (matchAny(clientIp, blockedList)) {
            log.warn("IP {} is blocked by blacklist", clientIp);
            return false;
        }

        if (allowedList.isEmpty()) {
            log.info("No whitelist configured, allow IP {}", clientIp);
            return true;
        }

        boolean allowed = matchAny(clientIp, allowedList);
        log.info("IP {} whitelist match result: {}", clientIp, allowed);
        return allowed;
    }

    public Map<String, Object> getSystemSettings() {
        Map<String, Object> result = new LinkedHashMap<>();
        List<SystemConfigEntity> publicConfigs = systemConfigMapper.findPublicConfigs();
        Set<String> publicKeys = publicConfigs.stream()
                .map(SystemConfigEntity::getConfigKey)
                .collect(Collectors.toSet());

        for (SystemConfigEntity config : publicConfigs) {
            result.put(config.getConfigKey(), config.getConfigValue());
        }

        // Also add non-public system.* configs
        List<SystemConfigEntity> systemConfigs = systemConfigMapper.findByKeyPrefix("setting.");
        for (SystemConfigEntity config : systemConfigs) {
            if (!publicKeys.contains(config.getConfigKey())) {
                result.put(config.getConfigKey(), config.getConfigValue());
            }
        }

        return result;
    }

    public Map<String, Object> updateSystemSettings(Map<String, Object> settings) {
        log.info("Updating system settings");
        for (Map.Entry<String, Object> entry : settings.entrySet()) {
            upsertConfig(entry.getKey(), String.valueOf(entry.getValue()), "string",
                    KEY_DESCRIPTIONS.getOrDefault(entry.getKey(), ""));
        }
        return getSystemSettings();
    }

    public Map<String, Object> getSecuritySettings() {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("password", getPasswordPolicy());
        result.put("login", getLoginSecuritySettings());
        result.put("ip", getIPAccessControl());
        return result;
    }

    public Map<String, Object> updateSecuritySettings(Map<String, Object> settings) {
        log.info("Updating security settings");
        for (Map.Entry<String, Object> entry : settings.entrySet()) {
            upsertConfig(entry.getKey(), String.valueOf(entry.getValue()), "string",
                    KEY_DESCRIPTIONS.getOrDefault(entry.getKey(), ""));
        }
        return getSecuritySettings();
    }

    public Map<String, Object> getPasswordPolicy() {
        Map<String, Object> result = new LinkedHashMap<>();
        List<SystemConfigEntity> configs = systemConfigMapper.findByKeyPrefix("setting.password.");
        for (SystemConfigEntity config : configs) {
            result.put(config.getConfigKey(), config.getConfigValue());
        }
        return result;
    }

    public Map<String, Object> updatePasswordPolicy(Map<String, Object> policy) {
        log.info("Updating password policy");
        for (Map.Entry<String, Object> entry : policy.entrySet()) {
            upsertConfig(entry.getKey(), String.valueOf(entry.getValue()), "string",
                    KEY_DESCRIPTIONS.getOrDefault(entry.getKey(), ""));
        }
        return getPasswordPolicy();
    }

    public Map<String, Object> getSessionSettings() {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put(SESSION_TOKEN_EXPIRE, loadConfig(SESSION_TOKEN_EXPIRE));
        result.put(SESSION_REFRESH_EXPIRE, loadConfig(SESSION_REFRESH_EXPIRE));
        result.put(SESSION_MAX_CONCURRENT, loadConfig(SESSION_MAX_CONCURRENT));
        return result;
    }

    public Map<String, Object> updateSessionSettings(Map<String, Object> settings) {
        log.info("Updating session settings");
        for (Map.Entry<String, Object> entry : settings.entrySet()) {
            upsertConfig(entry.getKey(), String.valueOf(entry.getValue()), "string", "Session setting");
        }
        return getSessionSettings();
    }

    public Map<String, Object> getLoginSecuritySettings() {
        Map<String, Object> result = new LinkedHashMap<>();
        List<SystemConfigEntity> configs = systemConfigMapper.findByKeyPrefix("setting.login.");
        for (SystemConfigEntity config : configs) {
            result.put(config.getConfigKey(), config.getConfigValue());
        }
        return result;
    }

    public Map<String, Object> updateLoginSecuritySettings(Map<String, Object> settings) {
        log.info("Updating login security settings");
        for (Map.Entry<String, Object> entry : settings.entrySet()) {
            upsertConfig(entry.getKey(), String.valueOf(entry.getValue()), "string",
                    KEY_DESCRIPTIONS.getOrDefault(entry.getKey(), ""));
        }
        return getLoginSecuritySettings();
    }

    public Map<String, Object> getIPAccessControl() {
        Map<String, Object> result = new HashMap<>();
        result.put("allowed_ips", loadConfig(ALLOWED_KEY));
        result.put("blocked_ips", loadConfig(BLOCKED_KEY));
        return result;
    }

    public Map<String, Object> updateIPAccessControl(Map<String, Object> settings) {
        log.info("Updating IP access control");
        if (settings.containsKey("allowed_ips")) {
            String value = String.join(",",
                    ((List<String>) settings.get("allowed_ips")));
            upsertConfig(ALLOWED_KEY, value, "string", KEY_DESCRIPTIONS.get(ALLOWED_KEY));
        }
        if (settings.containsKey("blocked_ips")) {
            String value = String.join(",",
                    ((List<String>) settings.get("blocked_ips")));
            upsertConfig(BLOCKED_KEY, value, "string", KEY_DESCRIPTIONS.get(BLOCKED_KEY));
        }
        return getIPAccessControl();
    }

    public Map<String, Object> addIPToWhitelist(Map<String, Object> data) {
        log.info("Adding IP to whitelist");
        String newIp = (String) data.get("ip");
        if (!StringUtils.hasText(newIp)) {
            return getIPAccessControl();
        }
        List<String> current = new ArrayList<>(loadConfig(ALLOWED_KEY));
        current.add(newIp.trim());
        String value = current.stream().distinct().collect(Collectors.joining(","));
        upsertConfig(ALLOWED_KEY, value, "string", KEY_DESCRIPTIONS.get(ALLOWED_KEY));
        return getIPAccessControl();
    }

    public Map<String, Object> removeIPFromWhitelist(Map<String, Object> data) {
        log.info("Removing IP from whitelist");
        String ipToRemove = (String) data.get("ip");
        if (!StringUtils.hasText(ipToRemove)) {
            return getIPAccessControl();
        }
        List<String> current = new ArrayList<>(loadConfig(ALLOWED_KEY));
        current.removeIf(ip -> ip.trim().equals(ipToRemove.trim()));
        String value = current.stream().collect(Collectors.joining(","));
        upsertConfig(ALLOWED_KEY, value, "string", KEY_DESCRIPTIONS.get(ALLOWED_KEY));
        return getIPAccessControl();
    }

    public Map<String, Object> bulkUpdateIPWhitelist(Map<String, Object> data) {
        log.info("Bulk updating IP whitelist");
        @SuppressWarnings("unchecked")
        List<String> ips = (List<String>) data.get("ips");
        if (ips != null) {
            String value = ips.stream().map(String::trim).collect(Collectors.joining(","));
            upsertConfig(ALLOWED_KEY, value, "string", KEY_DESCRIPTIONS.get(ALLOWED_KEY));
        }
        return getIPAccessControl();
    }

    public void clearIPWhitelist() {
        log.info("Clearing IP whitelist");
        upsertConfig(ALLOWED_KEY, "", "string", KEY_DESCRIPTIONS.get(ALLOWED_KEY));
    }

    public void resetToDefaults(String category) {
        log.info("Resetting {} to defaults", category);
        String prefix;
        switch (category.toLowerCase()) {
            case "system":
                prefix = "setting.";
                break;
            case "security":
                prefix = "setting.";
                break;
            case "password":
                prefix = "setting.password.";
                break;
            case "login":
                prefix = "setting.login.";
                break;
            case "ip":
                prefix = "setting.ip.";
                break;
            default:
                log.warn("Unknown category: {}", category);
                return;
        }
        // Delete existing configs in this category
        List<SystemConfigEntity> existing = systemConfigMapper.findByKeyPrefix(prefix);
        for (SystemConfigEntity config : existing) {
            systemConfigMapper.deleteByKey(config.getConfigKey());
        }
        // Re-insert defaults
        for (Map.Entry<String, String> entry : DEFAULTS.entrySet()) {
            if (entry.getKey().startsWith(prefix)) {
                upsertConfig(entry.getKey(), entry.getValue(), "string",
                        KEY_DESCRIPTIONS.getOrDefault(entry.getKey(), ""));
            }
        }
    }

    public Map<String, Object> exportAllSettings() {
        Map<String, Object> result = new LinkedHashMap<>();
        List<SystemConfigEntity> all = systemConfigMapper.findAll();
        for (SystemConfigEntity config : all) {
            result.put(config.getConfigKey(), config.getConfigValue());
        }
        return result;
    }

    public void importSettings(Map<String, Object> settings) {
        log.info("Importing settings");
        for (Map.Entry<String, Object> entry : settings.entrySet()) {
            upsertConfig(entry.getKey(), String.valueOf(entry.getValue()), "string",
                    KEY_DESCRIPTIONS.getOrDefault(entry.getKey(), ""));
        }
    }

    public Map<String, Object> clearRedisCache() {
        log.info("Clearing Redis cache");
        cacheService.clearAllCache();
        Map<String, Object> result = new HashMap<>();
        result.put("status", "success");
        result.put("message", "All Redis cache cleared");
        return result;
    }

    // ===== Helper Methods =====

    private void upsertConfig(String key, String value, String type, String description) {
        SystemConfigEntity existing = systemConfigMapper.findByKey(key);
        if (existing != null) {
            existing.setConfigValue(value);
            existing.setUpdatedAt(java.time.LocalDateTime.now());
            systemConfigMapper.update(existing);
        } else {
            SystemConfigEntity entity = new SystemConfigEntity(key, value, type, description, key.startsWith("setting."));
            systemConfigMapper.insert(entity);
        }
    }

    private List<String> loadConfig(String key) {
        SystemConfigEntity config = systemConfigMapper.findByKey(key);
        if (config == null || !StringUtils.hasText(config.getConfigValue())) {
            return Collections.emptyList();
        }
        return Arrays.stream(config.getConfigValue().split(","))
                .map(String::trim)
                .filter(StringUtils::hasText)
                .collect(Collectors.toList());
    }

    private boolean matchAny(String ip, List<String> rules) {
        for (String rule : rules) {
            if (match(ip, rule)) {
                return true;
            }
        }
        return false;
    }

    private boolean match(String ip, String rule) {
        try {
            if (rule.contains("/")) {
                return matchCidr(ip, rule);
            }
            if (rule.contains("-")) {
                return matchRange(ip, rule);
            }
            return ip.equals(rule);
        } catch (Exception e) {
            log.error("Invalid IP rule: {}", rule, e);
            return false;
        }
    }

    private boolean matchCidr(String ip, String cidr) throws Exception {
        String[] parts = cidr.split("/");
        InetAddress inetAddress = InetAddress.getByName(parts[0]);
        int prefix = Integer.parseInt(parts[1]);

        long mask = ~((1L << (32 - prefix)) - 1);
        long network = ipToLong(inetAddress.getHostAddress()) & mask;
        long target = ipToLong(ip) & mask;

        return network == target;
    }

    private boolean matchRange(String ip, String range) throws Exception {
        String[] parts = range.split("-");
        long start = ipToLong(parts[0]);
        long end = ipToLong(parts[1]);
        long target = ipToLong(ip);
        return target >= start && target <= end;
    }

    private long ipToLong(String ip) throws Exception {
        byte[] bytes = InetAddress.getByName(ip).getAddress();
        long result = 0;
        for (byte b : bytes) {
            result = result << 8 | (b & 0xFF);
        }
        return result;
    }
}
