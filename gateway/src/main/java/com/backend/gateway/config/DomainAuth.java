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
@Component("DomainAuth")
public class DomainAuth extends AuthFilter<BaseAuthConfig> {

    @Value("${secure.header.domain.secret:default-secret}")
    private String secret;

    @Value("${secure.domain.whitelist-paths:/health}")
    private String whitelistPaths;

    @Value("${secure.domain.authorized-methods:}")
    private String authorizedMethods;

    public DomainAuth(@Qualifier("blockingTaskScheduler") Scheduler scheduler, CacheManager cacheManager) {
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
