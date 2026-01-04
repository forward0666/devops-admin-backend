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

    private static final String ALLOWED_KEY = "security.ip.allowed_ips";
    private static final String BLOCKED_KEY = "security.ip.blocked_ips";

    /**
     * 是否允许该 IP 访问
     */
    public boolean isIpAllowed(String clientIp) {
        log.info("Fetching IP access control");

        List<String> allowedList = loadConfig(ALLOWED_KEY);
        List<String> blockedList = loadConfig(BLOCKED_KEY);

        // 1️⃣ 黑名单优先
        if (matchAny(clientIp, blockedList)) {
            log.warn("IP {} is blocked by blacklist", clientIp);
            return false;
        }

        // 2️⃣ 如果未配置白名单，默认放行
        if (allowedList.isEmpty()) {
            log.info("No whitelist configured, allow IP {}", clientIp);
            return true;
        }

        // 3️⃣ 命中白名单才放行
        boolean allowed = matchAny(clientIp, allowedList);
        log.info("IP {} whitelist match result: {}", clientIp, allowed);
        return allowed;
    }

    public Map<String, Object> getSystemSettings() {
        return new HashMap<>();
    }

    public Map<String, Object> updateSystemSettings(Map<String, Object> settings) {
        log.info("Updating system settings");
        return new HashMap<>();
    }

    public Map<String, Object> getSecuritySettings() {
        return new HashMap<>();
    }

    public Map<String, Object> updateSecuritySettings(Map<String, Object> settings) {
        log.info("Updating security settings");
        return new HashMap<>();
    }

    public Map<String, Object> getPasswordPolicy() {
        return new HashMap<>();
    }

    public Map<String, Object> updatePasswordPolicy(Map<String, Object> policy) {
        log.info("Updating password policy");
        return new HashMap<>();
    }

    public Map<String, Object> getLoginSecuritySettings() {
        return new HashMap<>();
    }

    public Map<String, Object> updateLoginSecuritySettings(Map<String, Object> settings) {
        log.info("Updating login security settings");
        return new HashMap<>();
    }

    public Map<String, Object> getIPAccessControl() {
        Map<String, Object> result = new HashMap<>();
        result.put("allowed_ips", loadConfig(ALLOWED_KEY));
        result.put("blocked_ips", loadConfig(BLOCKED_KEY));
        return result;
    }

    public Map<String, Object> updateIPAccessControl(Map<String, Object> settings) {
        log.info("Updating IP access control");
        return new HashMap<>();
    }

    public void resetToDefaults(String category) {
        log.info("Resetting {} to defaults", category);
    }

    public Map<String, Object> exportAllSettings() {
        return new HashMap<>();
    }

    public void importSettings(Map<String, Object> settings) {
        log.info("Importing settings");
    }

    public Map<String, Object> addIPToWhitelist(Map<String, Object> data) {
        log.info("Adding IP to whitelist");
        return new HashMap<>();
    }

    public Map<String, Object> removeIPFromWhitelist(Map<String, Object> data) {
        log.info("Removing IP from whitelist");
        return new HashMap<>();
    }

    public Map<String, Object> bulkUpdateIPWhitelist(Map<String, Object> data) {
        log.info("Bulk updating IP whitelist");
        return new HashMap<>();
    }

    public void clearIPWhitelist() {
        log.info("Clearing IP whitelist");
    }

    public Map<String, Object> clearRedisCache() {
        log.info("Clearing Redis cache");
        return new HashMap<>();
    }

    /**
     * 读取 system_configs
     */
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

    /**
     * 判断 IP 是否命中任一规则
     */
    private boolean matchAny(String ip, List<String> rules) {
        for (String rule : rules) {
            if (match(ip, rule)) {
                return true;
            }
        }
        return false;
    }

    /**
     * 单条规则匹配
     */
    private boolean match(String ip, String rule) {
        try {
            // CIDR
            if (rule.contains("/")) {
                return matchCidr(ip, rule);
            }

            // IP 段
            if (rule.contains("-")) {
                return matchRange(ip, rule);
            }

            // 单 IP
            return ip.equals(rule);

        } catch (Exception e) {
            log.error("Invalid IP rule: {}", rule, e);
            return false;
        }
    }

    /**
     * CIDR 匹配
     */
    private boolean matchCidr(String ip, String cidr) throws Exception {
        String[] parts = cidr.split("/");
        InetAddress inetAddress = InetAddress.getByName(parts[0]);
        int prefix = Integer.parseInt(parts[1]);

        long mask = ~((1L << (32 - prefix)) - 1);
        long network = ipToLong(inetAddress.getHostAddress()) & mask;
        long target = ipToLong(ip) & mask;

        return network == target;
    }

    /**
     * IP 段匹配
     */
    private boolean matchRange(String ip, String range) throws Exception {
        String[] parts = range.split("-");
        long start = ipToLong(parts[0]);
        long end = ipToLong(parts[1]);
        long target = ipToLong(ip);
        return target >= start && target <= end;
    }

    /**
     * IP 转 long
     */
    private long ipToLong(String ip) throws Exception {
        byte[] bytes = InetAddress.getByName(ip).getAddress();
        long result = 0;
        for (byte b : bytes) {
            result = result << 8 | (b & 0xFF);
        }
        return result;
    }
}
