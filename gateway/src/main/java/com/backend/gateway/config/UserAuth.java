package com.backend.gateway.config;

import com.backend.gateway.filter.AuthFilter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cloud.context.config.annotation.RefreshScope;
import org.springframework.stereotype.Component;
import org.springframework.cache.CacheManager;
import reactor.core.scheduler.Scheduler;

@Slf4j
@RefreshScope
@Component("user")
public class UserAuth extends AuthFilter<BaseAuthConfig> {

    @Value("${secure.user.whitelist-paths:}")
    private String whitelistPaths;

    public UserAuth(@Qualifier("blockingTaskScheduler") Scheduler scheduler, CacheManager cacheManager) {
        super(BaseAuthConfig.class, scheduler, cacheManager);
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
