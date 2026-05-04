package com.backend.gateway.config;

import com.backend.gateway.filter.AuthFilter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier; // 导入 Qualifier
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cloud.context.config.annotation.RefreshScope;
import org.springframework.stereotype.Component;
import reactor.core.scheduler.Scheduler; // 导入 Scheduler
import org.springframework.cache.CacheManager;

/**
 * 全局认证过滤器
 */
@Slf4j
@RefreshScope
@Component("GlobalAuth")
public class GlobalAuth extends AuthFilter<BaseAuthConfig> {

    @Value("${secure.header.global.secret:default-secret}")
    private String secret;

    @Value("${secure.global.whitelist-paths:}")
    private String whitelistPaths;

    @Value("${secure.global.authorized-methods:}")
    private String authorizedMethods;

    @Value("${secure.global.whitelist-paths:}")
    private String whitelistPaths;

    /**
     * 🌟 关键修改：使用 @Qualifier 注入 Scheduler
     */
    public GlobalAuth(@Qualifier("blockingTaskScheduler") Scheduler scheduler, CacheManager cacheManager) {
        super(BaseAuthConfig.class, scheduler, cacheManager); // 传递 scheduler
    }

    @Override
    protected String getSecret() {
        return secret;
    }

    @Override
    protected boolean authorizedRequest(String method) {
        if (authorizedMethods == null || authorizedMethods.isBlank()) {
            return false;
        }
        for (String m : authorizedMethods.split(",")) {
            if (m.trim().equalsIgnoreCase(method)) {
                return true;
            }
        }
        return false;
    }

    @Override
    protected boolean isWhitelistedPath(String path) {
        if (path == null || whitelistPaths == null || whitelistPaths.isBlank()) {
            return false;
        }
        for (String wp : whitelistPaths.split(",")) {
            String trimmed = wp.trim();
            if (!trimmed.isEmpty() && path.startsWith(trimmed)) {
                return true;
            }
        }
        return false;
    }
}