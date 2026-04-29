package com.backend.manage.filter;

import com.backend.manage.service.CacheService;
import com.backend.manage.util.JwtUtil;
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
    private JwtUtil jwtUtil;

    @Autowired
    private CacheService cacheService;

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {

        String path = request.getRequestURI();
        String method = request.getMethod();

        // Skip JWT validation for OPTIONS requests (CORS preflight)
        if ("OPTIONS".equals(method)) {
            filterChain.doFilter(request, response);
            return;
        }

        // Skip JWT validation for public endpoints
        if (path.equals("/login") || path.equals("/logout") || path.equals("/health")
                || path.equals("/actuator/health") || path.startsWith("/public/")) {
            filterChain.doFilter(request, response);
            return;
        }

        String authHeader = request.getHeader("Authorization");

        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            sendUnauthorizedResponse(response, "Authorization header required");
            return;
        }

        String token = authHeader.substring(7);

        try {
            // Validate token structure
            if (!jwtUtil.validateToken(token)) {
                log.warn("Invalid token structure for path: {}", path);
                sendUnauthorizedResponse(response, "Invalid or expired token");
                return;
            }

            String username = jwtUtil.getUsernameFromToken(token);

            // Check Redis cache first
            if (cacheService.isRedisAvailable() && username != null) {
                Boolean isValidToken = cacheService.getCachedTokenValidation(username, token);
                if (isValidToken != null) {
                    if (!isValidToken) {
                        log.warn("Token invalid according to Redis cache, user: {}, path: {}", username, path);
                        sendUnauthorizedResponse(response, "Invalid or expired token");
                        return;
                    }
                    log.debug("Token validated from Redis cache, user: {}, path: {}", username, path);
                } else {
                    // Cache miss - validate locally and cache result
                    log.debug("Token cache miss for user: {}, path: {}", username, path);
                    cacheService.cacheTokenValidation(username, token, true, 86400);
                }
            }

            // Extract user info from token
            String role = jwtUtil.getRoleFromToken(token);
            Long userId = jwtUtil.getUserIdFromToken(token);
            String email = jwtUtil.getEmailFromToken(token);

            log.debug("JWT validation successful for user: {} with role: {}", username, role);

            // Add user info to request attributes for controllers
            request.setAttribute("userId", userId);
            request.setAttribute("username", username);
            request.setAttribute("userRole", role);
            request.setAttribute("userEmail", email);
            request.setAttribute("isAuthenticated", true);

        } catch (Exception e) {
            log.error("JWT token validation error: {}", e.getMessage());
            sendUnauthorizedResponse(response, "Token validation failed");
            return;
        }

        filterChain.doFilter(request, response);
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
