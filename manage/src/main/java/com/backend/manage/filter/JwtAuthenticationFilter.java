package com.backend.manage.filter;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
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

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        String path = request.getRequestURI();
        String method = request.getMethod();

        if ("true".equals(request.getHeader(internalWhitelistHeader))) {
            filterChain.doFilter(request, response);
            return;
        }
        if ("OPTIONS".equals(method)) {
            filterChain.doFilter(request, response);
            return;
        }
        if (isWhitelisted(path)) {
            filterChain.doFilter(request, response);
            return;
        }

        // 信任 Gateway 上游认证
        String userId = request.getHeader("X-User-Id");
        if (userId != null && !userId.isBlank()) {
            request.setAttribute("userId", userId);
            request.setAttribute("userRole", request.getHeader("X-User-Role"));
            request.setAttribute("isAuthenticated", true);
            log.debug("Trusted auth from Gateway: userId={}", userId);
            filterChain.doFilter(request, response);
            return;
        }

        // 直接访问检查 Authorization
        String authHeader = request.getHeader("Authorization");
        if (authHeader != null && authHeader.startsWith("Bearer ")) {
            request.setAttribute("isAuthenticated", true);
            filterChain.doFilter(request, response);
            return;
        }

        log.warn("No auth for path: {}", path);
        sendUnauthorizedResponse(response, "Authorization required");
    }

    private boolean isWhitelisted(String path) {
        return path.equals("/login") || path.equals("/logout") ||
               path.equals("/authLogIn") || path.equals("/authLogOut") ||
               path.equals("/authSSO") || path.equals("/register") ||
               path.equals("/health") || path.equals("/actuator/health") ||
               path.startsWith("/actuator/");
    }

    private void sendUnauthorizedResponse(HttpServletResponse response, String message) throws IOException {
        response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
        response.setContentType("application/json");
        Map<String, Object> err = new HashMap<>();
        err.put("code", 401);
        err.put("message", message);
        err.put("success", false);
        response.getWriter().write(objectMapper.writeValueAsString(err));
    }
}
