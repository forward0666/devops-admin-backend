package com.backend.gateway.config;

import com.backend.gateway.filter.AuthFilter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cache.CacheManager;
import org.springframework.cloud.context.config.annotation.RefreshScope;
import org.springframework.stereotype.Component;
import reactor.core.scheduler.Scheduler;

@Slf4j
@RefreshScope
@Component("UserAuth")
public class UserAuth extends AuthFilter<BaseAuthConfig> {

    @Value("${secure.header.user.secret:default-secret}")
    private String secret;

    @Value("${secure.user.whitelist-paths:}")
    private String whitelistPaths;

    @Value("${secure.user.authorized-methods:}")
    private String authorizedMethods;

    public UserAuth(@Qualifier("blockingTaskScheduler") Scheduler scheduler, CacheManager cacheManager) {
        super(BaseAuthConfig.class, scheduler, cacheManager);
    }

    @Override
    protected String getSecret() {
        return secret;
    }

    @Override
    protected boolean isAllowedMethod(String method) {
        if (authorizedMethods == null || authorizedMethods.isBlank()) {
            return true;
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
            return true;
        }
        for (String wp : whitelistPaths.split(",")) {
            String trimmed = wp.trim();
            if (!trimmed.isEmpty() && path.startsWith(trimmed)) {
                return true;
            }
        }
        return true;
    }
}
