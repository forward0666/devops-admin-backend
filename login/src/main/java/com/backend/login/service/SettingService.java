package com.backend.login.service;

import com.backend.login.service.CacheService;
import com.backend.login.mapper.SettingMapper;
import com.backend.login.entity.SettingEntity;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
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

    private final SettingMapper systemConfigMapper;

    @Autowired
    @Lazy
    private CacheService cacheService;

    // IP Access Control
    private static final String ALLOWED_KEY = "setting.ip.allowed_ips";
    private static final String BLOCKED_KEY = "setting.ip.blocked_ips";
    private static final String WHITELIST_MODE_KEY = "setting.ip.whitelist_enabled";

    // System Settings
    private static final String SYS_NAME_KEY = "setting.name";
    private static final String SYS_LOGO_KEY = "setting.logo";
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
    private static final String SESSION_TOKEN_EXPIRE = "setting.session.token_expire";
    private static final String SESSION_REFRESH_EXPIRE = "setting.session.refresh_expire";
    private static final String SESSION_MAX_CONCURRENT = "setting.session.max_concurrent";

    // Default values
    private static final Map<String, String> DEFAULTS = new LinkedHashMap<>();
    static {
        DEFAULTS.put(SYS_NAME_KEY, "DevOps Admin");
        DEFAULTS.put(SYS_LOGO_KEY, "");
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

    // ===== Getters for other services =====

    public String getSystemName() {
        return getConfigValue(SYS_NAME_KEY, DEFAULTS.get(SYS_NAME_KEY));
    }

    public String getSystemLogo() {
        return getConfigValue(SYS_LOGO_KEY, DEFAULTS.get(SYS_LOGO_KEY));
    }

    public String getSystemTheme() {
        return getConfigValue(SYS_THEME_KEY, DEFAULTS.get(SYS_THEME_KEY));
    }

    public int getPasswordMinLength() {
        return Integer.parseInt(getConfigValue(SEC_PASSWORD_MIN_LEN, DEFAULTS.get(SEC_PASSWORD_MIN_LEN)));
    }

    public boolean isPasswordRequireUppercase() {
        return "true".equalsIgnoreCase(getConfigValue(SEC_PASSWORD_REQUIRE_UPPER, DEFAULTS.get(SEC_PASSWORD_REQUIRE_UPPER)));
    }

    public boolean isPasswordRequireNumber() {
        return "true".equalsIgnoreCase(getConfigValue(SEC_PASSWORD_REQUIRE_NUMBER, DEFAULTS.get(SEC_PASSWORD_REQUIRE_NUMBER)));
    }

    public boolean isPasswordRequireSpecial() {
        return "true".equalsIgnoreCase(getConfigValue(SEC_PASSWORD_REQUIRE_SPECIAL, DEFAULTS.get(SEC_PASSWORD_REQUIRE_SPECIAL)));
    }

    public int getPasswordExpireDays() {
        return Integer.parseInt(getConfigValue(SEC_PASSWORD_EXPIRE_DAYS, DEFAULTS.get(SEC_PASSWORD_EXPIRE_DAYS)));
    }

    public int getLoginMaxAttempts() {
        return Integer.parseInt(getConfigValue(SEC_LOGIN_MAX_ATTEMPTS, DEFAULTS.get(SEC_LOGIN_MAX_ATTEMPTS)));
    }

    public int getLoginLockoutMinutes() {
        return Integer.parseInt(getConfigValue(SEC_LOGIN_LOCKOUT_MINUTES, DEFAULTS.get(SEC_LOGIN_LOCKOUT_MINUTES)));
    }

    public boolean isLoginCaptchaEnabled() {
        return "true".equalsIgnoreCase(getConfigValue(SEC_LOGIN_CAPTCHA_ENABLED, DEFAULTS.get(SEC_LOGIN_CAPTCHA_ENABLED)));
    }

    public long getTokenExpireSeconds() {
        return Long.parseLong(getConfigValue(SESSION_TOKEN_EXPIRE, DEFAULTS.get(SESSION_TOKEN_EXPIRE)));
    }

    public long getRefreshTokenExpireSeconds() {
        return Long.parseLong(getConfigValue(SESSION_REFRESH_EXPIRE, DEFAULTS.get(SESSION_REFRESH_EXPIRE)));
    }

    public int getMaxConcurrentSession() {
        return Integer.parseInt(getConfigValue(SESSION_MAX_CONCURRENT, DEFAULTS.get(SESSION_MAX_CONCURRENT)));
    }

    /**
     * Validate password against policy
     * @return error message or null if valid
     */
    public String validatePassword(String password) {
        if (password == null || password.isEmpty()) {
            return "Password cannot be empty";
        }
        if (password.length() < getPasswordMinLength()) {
            return "Password must be at least " + getPasswordMinLength() + " characters";
        }
        if (isPasswordRequireUppercase() && !password.matches(".*[A-Z].*")) {
            return "Password must contain at least one uppercase letter";
        }
        if (isPasswordRequireNumber() && !password.matches(".*[0-9].*")) {
            return "Password must contain at least one number";
        }
        if (isPasswordRequireSpecial() && !password.matches(".*[!@#$%^&*()_+\\-=\\[\\]{};':\"\\\\|,.<>/?].*")) {
            return "Password must contain at least one special character";
        }
        return null;
    }

    /**
     * Check if IP is allowed
     */
    public boolean isIpAllowed(String clientIp) {
        List<String> blockedList = loadIpList(BLOCKED_KEY);
        List<String> allowedList = loadIpList(ALLOWED_KEY);

        // Blocked list always enforced
        if (matchAny(clientIp, blockedList)) {
            log.warn("IP {} is blocked", clientIp);
            return false;
        }

        // Check whitelist mode
        boolean whitelistEnabled = isWhitelistModeEnabled();
        if (!whitelistEnabled) {
            // Whitelist mode off: only blocked list matters
            return true;
        }

        // Whitelist mode on: must be in allowed list
        if (allowedList.isEmpty()) {
            return true;
        }

        // 0.0.0.0 means allow all
        if (allowedList.contains("0.0.0.0")) {
            return true;
        }

        return matchAny(clientIp, allowedList);
    }

    private boolean isWhitelistModeEnabled() {
        SettingEntity config = systemConfigMapper.findByKey(WHITELIST_MODE_KEY);
        if (config == null || !StringUtils.hasText(config.getConfigValue())) {
            return false;
        }
        return "true".equalsIgnoreCase(config.getConfigValue().trim());
    }

    public Map<String, Object> getIPAccessControl() {
        Map<String, Object> result = new HashMap<>();
        result.put("allowed_ips", String.join(",", loadIpList(ALLOWED_KEY)));
        result.put("blocked_ips", String.join(",", loadIpList(BLOCKED_KEY)));
        return result;
    }

    /**
     * Check if user login is locked out
     */
    public boolean isLoginLocked(String username) {
        String lockKey = "login:lock:" + username;
        Object locked = cacheService.get(lockKey);
        return locked != null && Boolean.TRUE.equals(locked);
    }

    /**
     * Record a failed login attempt, return true if locked
     */
    public boolean recordFailedLogin(String username) {
        String failKey = "login:fail:" + username;
        String lockKey = "login:lock:" + username;

        // Increment fail count
        int fails = cacheService.increment(failKey) != null ? (int)(long)cacheService.increment(failKey) : 1;
        // Set TTL on fail count
        cacheService.set(failKey, fails, getLoginLockoutMinutes(), java.util.concurrent.TimeUnit.MINUTES);

        int maxAttempts = getLoginMaxAttempts();
        if (fails >= maxAttempts) {
            cacheService.set(lockKey, true, getLoginLockoutMinutes(), java.util.concurrent.TimeUnit.MINUTES);
            log.warn("User {} locked after {} failed attempts", username, fails);
            return true;
        }
        return false;
    }

    /**
     * Clear failed login attempts
     */
    public void clearFailedLogin(String username) {
        cacheService.delete("login:fail:" + username);
        cacheService.delete("login:lock:" + username);
    }

    /**
     * Get remaining failed attempts before lockout
     */
    public int getRemainingAttempts(String username) {
        String failKey = "login:fail:" + username;
        Object val = cacheService.get(failKey);
        if (val == null) return getLoginMaxAttempts();
        int fails = ((Number) val).intValue();
        return Math.max(0, getLoginMaxAttempts() - fails);
    }

    // ===== CRUD =====

    public Map<String, Object> getSetting() {
        Map<String, Object> result = new LinkedHashMap<>();
        List<SettingEntity> allConfigs = systemConfigMapper.findAll();
        for (SettingEntity config : allConfigs) {
            result.put(config.getConfigKey(), config.getConfigValue());
        }
        return result;
    }

    public void updateSetting(Map<String, Object> settings) {
        for (Map.Entry<String, Object> entry : settings.entrySet()) {
            upsertConfig(entry.getKey(), String.valueOf(entry.getValue()), "string", "");
        }
    }

    public void resetToDefaults(String category) {
        log.info("Resetting {} to defaults", category);
        String prefix = switch (category.toLowerCase()) {
            case "system" -> "setting.";
            case "security", "password" -> "setting.password.";
            case "login" -> "setting.login.";
            case "ip" -> "setting.ip.";
            case "session" -> "setting.session.";
            default -> {
                log.warn("Unknown category: {}", category);
                yield "";
            }
        };
        if (prefix.isEmpty()) return;

        List<SettingEntity> existing = systemConfigMapper.findByKeyPrefix(prefix);
        for (SettingEntity config : existing) {
            systemConfigMapper.deleteByKey(config.getConfigKey());
        }
        for (Map.Entry<String, String> entry : DEFAULTS.entrySet()) {
            if (entry.getKey().startsWith(prefix)) {
                upsertConfig(entry.getKey(), entry.getValue(), "string", "");
            }
        }
    }

    // ===== Helper Methods =====

    private String getConfigValue(String key, String defaultVal) {
        SettingEntity config = systemConfigMapper.findByKey(key);
        if (config != null && StringUtils.hasText(config.getConfigValue())) {
            return config.getConfigValue();
        }
        return defaultVal;
    }

    private void upsertConfig(String key, String value, String type, String description) {
        SettingEntity existing = systemConfigMapper.findByKey(key);
        if (existing != null) {
            existing.setConfigValue(value);
            existing.setUpdatedAt(java.time.LocalDateTime.now());
            systemConfigMapper.update(existing);
        } else {
            SettingEntity entity = new SettingEntity(key, value, type, description, key.startsWith("setting."));
            systemConfigMapper.insert(entity);
        }
    }

    private List<String> loadIpList(String key) {
        SettingEntity config = systemConfigMapper.findByKey(key);
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
            if (match(ip, rule)) return true;
        }
        return false;
    }

    private boolean match(String ip, String rule) {
        try {
            if (rule.contains("/")) return matchCidr(ip, rule);
            if (rule.contains("-")) return matchRange(ip, rule);
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
