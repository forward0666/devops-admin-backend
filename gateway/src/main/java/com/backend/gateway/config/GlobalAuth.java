package com.backend.gateway.config;

import com.backend.gateway.filter.AuthFilter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cloud.context.config.annotation.RefreshScope;
import org.springframework.stereotype.Component;

import java.util.concurrent.ExecutorService;
import org.springframework.cache.CacheManager;

/**
 * 全局认证过滤器
 * 支持线程池异步验证 & traceId
 */
@Slf4j
@RefreshScope
@Component("GlobalAuth")
public class GlobalAuth extends AuthFilter<BaseAuthConfig> {

    @Value("${secure.header.global.secret:default-secret}")
    private String secret;

    /**
     * 使用构造器注入线程池
     */
    public GlobalAuth(ExecutorService executorService, CacheManager cacheManager) {
        super(BaseAuthConfig.class, executorService, cacheManager);
    }

    @Override
    protected String getSecret() {
        return secret;
    }

    /**
     * 可以在这里覆盖 authorizedRequest() 做方法级拦截
     */
    @Override
    protected boolean authorizedRequest(String method) {
        // 例如阻止 GET 请求，默认不阻止
        return false;
    }
}
