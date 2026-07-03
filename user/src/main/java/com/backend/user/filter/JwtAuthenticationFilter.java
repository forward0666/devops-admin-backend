package com.backend.user.filter;

import com.backend.utils.CacheService;
import com.backend.utils.JwtUtil;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

@Slf4j
@Component
public class JwtAuthenticationFilter extends OncePerRequestFilter {
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Value("${internal_whitelist_header:X-Internal-Call}")
    private String internalWhitelistHeader;

    @Autowired
    private CacheService cacheService;

    @Autowired
    private JwtUtil jwtUtil;

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {

        String path = request.getRequestURI();
        String method = request.getMethod();

        // Skip JWT for direct internal service calls (X-Internal-Call)
        String internalCall = request.getHeader(internalWhitelistHeader);
        if ("true".equals(internalCall)) {
            log.debug("Skipping JWT validation for internal call to path: {}", path);
            filterChain.doFilter(request, response);
            return;
        }

        // Skip JWT validation for internal Telegram bot calls
        String tgUsername = request.getHeader("X-Tg-Username");
        if (tgUsername != null && !tgUsername.isBlank()) {
            log.debug("Skipping JWT validation for TG bot call to path: {}, username={}", path, tgUsername);
            filterChain.doFilter(request, response);
            return;
        }

        // Skip JWT validation for OPTIONS requests (CORS preflight)
        if ("OPTIONS".equals(method)) {
            log.debug("Skipping JWT validation for OPTIONS request to path: {}", path);
            filterChain.doFilter(request, response);
            return;
        }

        // Skip JWT validation for login endpoint, logout, and health checks
        if (path.equals("/login") || path.equals("/logout") || path.equals("/health") || path.equals("/actuator/health")) {
            log.debug("Skipping JWT validation for path: {}", path);
            filterChain.doFilter(request, response);
            return;
        }

        String authHeader = request.getHeader("Authorization");

        if (authHeader != null && authHeader.startsWith("Bearer ")) {
            String token = authHeader.substring(7);

            try {
                // Extract username from token for cache key
                String username = extractUsernameFromToken(token);

                // First check if token exists in Redis
                Boolean isValidToken = null;
                if (cacheService.isRedisAvailable()) {
                    isValidToken = cacheService.getCachedTokenValidation(username, token);
                    if (isValidToken != null) {
                        log.debug("Token validation from Redis cache: {}，path: {}", isValidToken, path);
                        if (!isValidToken) {
                            log.warn("Token validation failed - token is invalid according to Redis cache");
                            sendUnauthorizedResponse(response, "Invalid or expired token");
                            return;
                        }
                    }
                }

                // If not in Redis or Redis is unavailable, validate locally
                if (isValidToken == null) {
                    try {
                        // Validate token locally using JwtUtil
                        Long userId = jwtUtil.getUserIdFromToken(token);
                        if (userId == null) {
                            log.warn("Token validation failed - could not extract userId");
                            sendUnauthorizedResponse(response, "Invalid or expired token");
                            return;
                        }

                        log.debug("Token validation successful locally");

                        // Store validation result in Redis
                        if (cacheService.isRedisAvailable()) {
                            cacheService.cacheTokenValidation(username, token, true);
                            log.debug("Stored token validation result in Redis");
                        }
                    } catch (Exception e) {
                        log.error("Error validating token: {}", e.getMessage());
                        sendUnauthorizedResponse(response, "Token validation error: " + e.getMessage());
                        return;
                    }
                }

                // Extract token info and add to request attributes
                Map<String, Object> tokenData = extractTokenInfo(token);

                if (tokenData != null) {
                    String extractedUsername = null;
                    if (tokenData.containsKey("username")) {
                        extractedUsername = (String) tokenData.get("username");
                    } else if (tokenData.containsKey("sub")) {
                        extractedUsername = (String) tokenData.get("sub");
                    } else if (tokenData.containsKey("subject")) {
                        extractedUsername = (String) tokenData.get("subject");
                    }

                    String role = null;
                    if (tokenData.containsKey("role")) {
                        role = (String) tokenData.get("role");
                    } else if (tokenData.containsKey("authorities")) {
                        Object authorities = tokenData.get("authorities");
                        if (authorities instanceof String) {
                            role = (String) authorities;
                        }
                    } else if (tokenData.containsKey("claims")) {
                        Object claimsObj = tokenData.get("claims");
                        if (claimsObj instanceof Map) {
                            @SuppressWarnings("unchecked")
                            Map<String, Object> claims = (Map<String, Object>) claimsObj;
                            if (claims.containsKey("role")) {
                                role = (String) claims.get("role");
                            }
                        }
                    }

                    log.debug("Extracted role from token: {}", role);

                    Object userIdObj = tokenData.get("userId");
                    Integer userId = null;
                    if (userIdObj instanceof Integer) {
                        userId = (Integer) userIdObj;
                    } else if (userIdObj instanceof Number) {
                        userId = ((Number) userIdObj).intValue();
                    } else if (userIdObj instanceof String) {
                        try {
                            userId = Integer.parseInt((String) userIdObj);
                        } catch (NumberFormatException e) {
                            log.warn("Could not parse userId as integer: {}", userIdObj);
                        }
                    }

                    String email = (String) tokenData.get("email");

                    log.debug("JWT validation successful for user: {} with role: {}", extractedUsername, role);

                    request.setAttribute("userId", userId);
                    request.setAttribute("username", extractedUsername);
                    request.setAttribute("userRole", role);
                    request.setAttribute("userEmail", email);
                    request.setAttribute("isAuthenticated", true);

                } else {
                    log.warn("JWT token validation failed - invalid token structure");
                    sendUnauthorizedResponse(response, "Invalid or expired token");
                    return;
                }

            } catch (Exception e) {
                log.error("JWT token validation error: {}", e.getMessage());
                sendUnauthorizedResponse(response, "Token validation failed: " + e.getMessage());
                return;
            }
        } else {
            log.warn("No Authorization header found for path: {}", path);
            sendUnauthorizedResponse(response, "Authorization header required");
            return;
        }

        filterChain.doFilter(request, response);
    }

    /**
     * Extract username from JWT token
     */
    private String extractUsernameFromToken(String token) {
        try {
            String[] parts = token.split("\\.");
            if (parts.length != 3) {
                return null;
            }

            String payload = new String(java.util.Base64.getUrlDecoder().decode(parts[1]));
            Map<String, Object> claims = objectMapper.readValue(payload, Map.class);

            if (claims.containsKey("username")) {
                return (String) claims.get("username");
            } else if (claims.containsKey("sub")) {
                return (String) claims.get("sub");
            } else if (claims.containsKey("subject")) {
                return (String) claims.get("subject");
            }
            return null;
        } catch (Exception e) {
            log.error("Error extracting username from token: {}", e.getMessage());
            return null;
        }
    }

    /**
     * Extract user information from JWT token
     */
    @SuppressWarnings("unchecked")
    private Map<String, Object> extractTokenInfo(String token) {
        try {
            String[] parts = token.split("\\.");
            if (parts.length != 3) {
                return null;
            }

            String payload = new String(java.util.Base64.getUrlDecoder().decode(parts[1]));
            Map<String, Object> claims = objectMapper.readValue(payload, Map.class);

            log.debug("Extracted token claims: {}", claims);

            Map<String, Object> userInfo = new HashMap<>();
            userInfo.put("valid", true);
            userInfo.putAll(claims);

            if (!userInfo.containsKey("username") && userInfo.containsKey("sub")) {
                userInfo.put("username", claims.get("sub"));
            }

            return userInfo;

        } catch (Exception e) {
            log.error("Error extracting token info: {}", e.getMessage());
            return null;
        }
    }

    private void sendUnauthorizedResponse(HttpServletResponse response, String message) throws IOException {
        response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
        response.setContentType("application/json");
        response.setCharacterEncoding("UTF-8");

        Map<String, Object> errorResponse = new HashMap<>();
        errorResponse.put("code", 401);
        errorResponse.put("message", message);
        errorResponse.put("success", false);

        String jsonResponse = objectMapper.writeValueAsString(errorResponse);
        response.getWriter().write(jsonResponse);
    }
}
