package com.backend.login.filter;

import com.backend.utils.CacheService;
import com.backend.login.service.AuthService;
import com.backend.login.client.SecurityServiceClient;
import com.backend.utils.CacheService;
import com.backend.login.service.AuthService;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

@Slf4j
@Component
public class JwtAuthenticationFilter extends OncePerRequestFilter {
    private final ObjectMapper objectMapper = new ObjectMapper();
    
    @Autowired
    private SecurityServiceClient securityServiceClient;
    
    @Autowired
    private CacheService cacheService;
    @Autowired
    private AuthService authService;

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {

        String path = request.getRequestURI();
        String method = request.getMethod();
        
        // Skip JWT validation for OPTIONS requests (CORS preflight)
        if ("OPTIONS".equals(method)) {
            log.debug("Skipping JWT validation for OPTIONS request to path: {}", path);
            filterChain.doFilter(request, response);
            return;
        }
        
        // Skip JWT validation for login endpoint, logout, and health checks
        if (path.equals("/authLogIn") || path.equals("/authLogOut") || path.equals("/authSSO") || path.equals("/validate-token") || path.equals("/health") || path.equals("/actuator/health")) {
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
                        log.info("Token validation from Redis cache: {}，path: {}", isValidToken,path);
                        if (!isValidToken) {
                            log.warn("Token validation failed - token is invalid according to Redis cache");
                            sendUnauthorizedResponse(response, "Invalid or expired token");
                            return;
                        }
                    }
                }

                // If not in Redis or Redis is unavailable, use security service via Feign
                Map<String, Object> tokenData = null;
                if (isValidToken == null) {
                    try {
                        // Call security service via Feign client
                        Map<String, String> requestBody = new HashMap<>();
                        requestBody.put("token", token);

                        log.info("Validating token with security service via Feign");
                        Map<String, Object> serviceResponse = securityServiceClient.validateToken(requestBody);

                        if (serviceResponse != null) {
                            Boolean valid = (Boolean) serviceResponse.get("valid");
                            if (Boolean.TRUE.equals(valid)) {
                                log.info("Token validation successful via security service");

                                // Extract token data
                                tokenData = extractTokenInfo(token);

                                // Store validation result in Redis
                                if (cacheService.isRedisAvailable()) {
                                    cacheService.cacheTokenValidation(username, token, true, 86400);
                                    log.info("Stored token validation result in Redis");
                                }
                            } else {
                                log.warn("Token validation failed: {}", serviceResponse.get("message"));
                                sendUnauthorizedResponse(response, "Invalid or expired token");
                                return;
                            }
                        } else {
                            log.warn("Security service returned null response for token validation");
                            sendUnauthorizedResponse(response, "Token validation failed");
                            return;
                        }
                    } catch (Exception e) {
                        log.error("Error validating token with security service: {}", e.getMessage());
                        sendUnauthorizedResponse(response, "Token validation error: " + e.getMessage());
                        return;
                    }
                } else {
                    // If token is valid in Redis, extract info for request attributes
                    tokenData = extractTokenInfo(token);
                }
                
                if (tokenData != null) {
                    // Try to get username from different possible fields
                    String extractedUsername = null;
                    if (tokenData.containsKey("username")) {
                        extractedUsername = (String) tokenData.get("username");
                    } else if (tokenData.containsKey("sub")) {
                        extractedUsername = (String) tokenData.get("sub");
                    } else if (tokenData.containsKey("subject")) {
                        extractedUsername = (String) tokenData.get("subject");
                    }
                    
                    // Try to get role from different possible fields
                    String role = null;
                    if (tokenData.containsKey("role")) {
                        role = (String) tokenData.get("role");
                    } else if (tokenData.containsKey("authorities")) {
                        Object authorities = tokenData.get("authorities");
                        if (authorities instanceof String) {
                            role = (String) authorities;
                        }
                    } else if (tokenData.containsKey("claims")) {
                        // Extract role from nested claims object
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

                    // Get userId
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

                    // Get email
                    String email = (String) tokenData.get("email");

                    log.info("JWT validation successful for user: {} with role: {}", extractedUsername, role);

                    // Add user info to request attributes for controllers
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

            // Decode payload (base64)
            String payload = new String(java.util.Base64.getUrlDecoder().decode(parts[1]));
            Map<String, Object> claims = objectMapper.readValue(payload, Map.class);

            // Try to get username from different possible fields
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
            // This is a simple JWT parsing without signature verification
            // The security service should handle the actual validation
            String[] parts = token.split("\\.");
            if (parts.length != 3) {
                return null;
            }

            // Decode payload (base64)
            String payload = new String(java.util.Base64.getUrlDecoder().decode(parts[1]));
            Map<String, Object> claims = objectMapper.readValue(payload, Map.class);

            log.info("Extracted token claims: {}", claims);

            Map<String, Object> userInfo = new HashMap<>();
            userInfo.put("valid", true);

            // Copy all claims to the userInfo map
            userInfo.putAll(claims);

            // Ensure standard fields are present
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
