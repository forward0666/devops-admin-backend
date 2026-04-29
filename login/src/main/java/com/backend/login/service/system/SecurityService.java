package com.backend.login.service.system;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.net.InetAddress;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Slf4j
@Service
public class SecurityService {

    @Autowired
    private SettingService systemSettingsService;

    /**
     * Check if the given IP address is allowed based on the whitelist configuration
     * 
     * @return true if the IP is allowed, false otherwise
     */

    @SuppressWarnings("unchecked")
    private List<String> parseIPConfig(Object value) {
        if (value == null) {
            return List.of();
        }

        // 情况 1：String（兼容旧配置）
        if (value instanceof String s) {
            return Arrays.stream(s.split("[,\n]"))
                    .map(String::trim)
                    .filter(v -> !v.isEmpty())
                    .collect(Collectors.toList());
        }

        // 情况 2：List（Nacos / JSON 推荐）
        if (value instanceof List<?> list) {
            return list.stream()
                    .filter(v -> v != null && !v.toString().isBlank())
                    .map(Object::toString)
                    .collect(Collectors.toList());
        }

        log.warn("Unsupported IP config type: {}", value.getClass());
        return List.of();
    }

//    public boolean isIPAllowed(String clientIP) {
//        try {
//            log.info("Checking IP whitelist for client IP: {}", clientIP);
//
//            // Get allowed IPs from cached IP access control settings
//            Map<String, Object> ipSettings = systemSettingsService.getIPAccessControl();
//            String allowedIPsConfig = (String) ipSettings.getOrDefault("allowedIPs", "");
//
//            // If no whitelist is configured, allow all IPs (default behavior)
//            if (allowedIPsConfig == null || allowedIPsConfig.trim().isEmpty()) {
//                log.info("No IP whitelist configured, allowing all IPs");
//                return true;
//            }
//
//            // Parse the allowed IPs configuration
//            List<String> allowedIPs = Arrays.stream(allowedIPsConfig.split("\n"))
//                    .map(String::trim)
//                    .filter(ip -> !ip.isEmpty())
//                    .collect(Collectors.toList());
//
//            if (allowedIPs.isEmpty()) {
//                log.info("Empty IP whitelist, allowing all IPs");
//                return true;
//            }
//
//            log.info("Checking against {} configured allowed IPs", allowedIPs.size());
//
//            // Check if client IP matches any allowed IP or IP range
//            for (String allowedIP : allowedIPs) {
//                if (isIPMatched(clientIP, allowedIP)) {
//                    log.info("Client IP {} matched allowed IP/range: {}", clientIP, allowedIP);
//                    return true;
//                }
//            }
//
//            log.warn("Client IP {} is not in the whitelist", clientIP);
//            return false;
//
//        } catch (Exception e) {
//            log.error("Error checking IP whitelist for {}: {}", clientIP, e.getMessage());
//            // In case of error, allow access to prevent lockout (fail-open approach)
//            return true;
//        }
//    }
public boolean isIPAllowed(String clientIP) {
    try {
        Map<String, Object> ipSettings = systemSettingsService.getIPAccessControl();

        List<String> allowedIPs = parseIPConfig(ipSettings.get("allowedIPs"));

        if (allowedIPs.isEmpty()) {
            return true; // 白名单为空 = 放行
        }

        for (String allowedIP : allowedIPs) {
            if (isIPMatched(clientIP, allowedIP)) {
                return true;
            }
        }

        return false;
    } catch (Exception e) {
        log.error("Error checking IP whitelist for {}", clientIP, e);
        return true; // fail-open
    }
}

    /**
     * Check if the given IP address is blocked based on the blacklist configuration
     * 
     * @param clientIP The client IP address to check
     * @return true if the IP is blocked, false otherwise
     */
//    public boolean isIPBlocked(String clientIP) {
//        try {
//            log.info("Checking IP blacklist for client IP: {}", clientIP);
//
//            // Get blocked IPs from cached IP access control settings
//            Map<String, Object> ipSettings = systemSettingsService.getIPAccessControl();
//            String blockedIPsConfig = (String) ipSettings.getOrDefault("blockedIPs", "");
//
//            // If no blacklist is configured, don't block any IPs
//            if (blockedIPsConfig == null || blockedIPsConfig.trim().isEmpty()) {
//                log.info("No IP blacklist configured, not blocking any IPs");
//                return false;
//            }
//
//            // Parse the blocked IPs configuration
//            List<String> blockedIPs = Arrays.stream(blockedIPsConfig.split("\n"))
//                    .map(String::trim)
//                    .filter(ip -> !ip.isEmpty())
//                    .collect(Collectors.toList());
//
//            if (blockedIPs.isEmpty()) {
//                log.info("Empty IP blacklist, not blocking any IPs");
//                return false;
//            }
//
//            log.info("Checking against {} configured blocked IPs", blockedIPs.size());
//
//            // Check if client IP matches any blocked IP or IP range
//            for (String blockedIP : blockedIPs) {
//                if (isIPMatched(clientIP, blockedIP)) {
//                    log.warn("Client IP {} matched blocked IP/range: {}", clientIP, blockedIP);
//                    return true;
//                }
//            }
//
//            log.info("Client IP {} is not in the blacklist", clientIP);
//            return false;
//
//        } catch (Exception e) {
//            log.error("Error checking IP blacklist for {}: {}", clientIP, e.getMessage());
//            // In case of error, don't block access
//            return false;
//        }
//    }
    public boolean isIPBlocked(String clientIP) {
        try {
            Map<String, Object> ipSettings = systemSettingsService.getIPAccessControl();

            List<String> blockedIPs = parseIPConfig(ipSettings.get("blockedIPs"));

            for (String blockedIP : blockedIPs) {
                if (isIPMatched(clientIP, blockedIP)) {
                    return true;
                }
            }

            return false;
        } catch (Exception e) {
            log.error("Error checking IP blacklist for {}", clientIP, e);
            return false;
        }
    }

    /**
     * Check if a client IP matches an allowed/blocked IP pattern
     * Supports individual IPs and CIDR notation
     * 
     * @param clientIP The client IP to check
     * @param configuredIP The configured IP pattern (can be single IP or CIDR)
     * @return true if the client IP matches the pattern
     */
    private boolean isIPMatched(String clientIP, String configuredIP) {
        try {
            // Handle CIDR notation (e.g., 192.168.1.0/24)
            if (configuredIP.contains("/")) {
                return isIPInCIDR(clientIP, configuredIP);
            }
            
            // Handle wildcard notation (e.g., 192.168.1.*)
            if (configuredIP.contains("*")) {
                return isIPMatchedWithWildcard(clientIP, configuredIP);
            }
            
            // Handle exact IP match
            return clientIP.equals(configuredIP);
            
        } catch (Exception e) {
            log.error("Error matching IP {} against pattern {}: {}", clientIP, configuredIP, e.getMessage());
            return false;
        }
    }
    
    /**
     * Check if an IP is within a CIDR range
     * 
     * @param clientIP The client IP to check
     * @param cidr The CIDR notation (e.g., 192.168.1.0/24)
     * @return true if the IP is within the CIDR range
     */
    private boolean isIPInCIDR(String clientIP, String cidr) {
        try {
            String[] parts = cidr.split("/");
            if (parts.length != 2) {
                return false;
            }
            
            InetAddress clientAddr = InetAddress.getByName(clientIP);
            InetAddress networkAddr = InetAddress.getByName(parts[0]);
            int prefixLength = Integer.parseInt(parts[1]);
            
            byte[] clientBytes = clientAddr.getAddress();
            byte[] networkBytes = networkAddr.getAddress();
            
            if (clientBytes.length != networkBytes.length) {
                return false;
            }
            
            int bytesToCheck = prefixLength / 8;
            int bitsToCheck = prefixLength % 8;
            
            // Check full bytes
            for (int i = 0; i < bytesToCheck; i++) {
                if (clientBytes[i] != networkBytes[i]) {
                    return false;
                }
            }
            
            // Check remaining bits
            if (bitsToCheck > 0 && bytesToCheck < clientBytes.length) {
                int mask = 0xFF << (8 - bitsToCheck);
                return (clientBytes[bytesToCheck] & mask) == (networkBytes[bytesToCheck] & mask);
            }
            
            return true;
            
        } catch (Exception e) {
            log.error("Error checking CIDR match for {} in {}: {}", clientIP, cidr, e.getMessage());
            return false;
        }
    }
    
    /**
     * Check if an IP matches a wildcard pattern
     * 
     * @param clientIP The client IP to check
     * @param pattern The wildcard pattern (e.g., 192.168.1.*)
     * @return true if the IP matches the pattern
     */
    private boolean isIPMatchedWithWildcard(String clientIP, String pattern) {
        try {
            String[] clientParts = clientIP.split("\\.");
            String[] patternParts = pattern.split("\\.");
            
            if (clientParts.length != 4 || patternParts.length != 4) {
                return false;
            }
            
            for (int i = 0; i < 4; i++) {
                if (!"*".equals(patternParts[i]) && !clientParts[i].equals(patternParts[i])) {
                    return false;
                }
            }
            
            return true;
            
        } catch (Exception e) {
            log.error("Error checking wildcard match for {} against {}: {}", clientIP, pattern, e.getMessage());
            return false;
        }
    }
    
    /**
     * Get the real client IP address from the HTTP request
     * Handles various proxy headers
     * 
     * @param xForwardedFor X-Forwarded-For header value
     * @param xRealIP X-Real-IP header value
     * @param remoteAddr Remote address from request
     * @return The real client IP address
     */
    public String getRealClientIP(String xForwardedFor, String xRealIP, String remoteAddr) {
        String clientIP = null;
        
        // Check X-Forwarded-For header (may contain multiple IPs)
        if (xForwardedFor != null && !xForwardedFor.isEmpty() && !"unknown".equalsIgnoreCase(xForwardedFor)) {
            // Get the first IP from the comma-separated list
            clientIP = xForwardedFor.split(",")[0].trim();
        }
        
        // Check X-Real-IP header
        if ((clientIP == null || clientIP.isEmpty() || "unknown".equalsIgnoreCase(clientIP)) 
            && xRealIP != null && !xRealIP.isEmpty() && !"unknown".equalsIgnoreCase(xRealIP)) {
            clientIP = xRealIP;
        }
        
        // Fall back to remote address
        if (clientIP == null || clientIP.isEmpty() || "unknown".equalsIgnoreCase(clientIP)) {
            clientIP = remoteAddr;
        }
        
        // Handle localhost variations
        if ("0:0:0:0:0:0:0:1".equals(clientIP) || "::1".equals(clientIP)) {
            clientIP = "127.0.0.1";
        }
        
        return clientIP;
    }
}
