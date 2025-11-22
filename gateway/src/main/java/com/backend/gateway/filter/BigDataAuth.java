package com.backend.gateway.filter;

import com.backend.gateway.config.BaseAuthConfig;
import com.backend.gateway.filter.AbstractAuthFilter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cache.CacheManager;
import org.springframework.cloud.context.config.annotation.RefreshScope;
import org.springframework.stereotype.Component;

import java.util.Set;
import java.util.concurrent.ExecutorService;

/**
 * BigData应用认证过滤器
 * 支持线程池异步验证 & 方法级阻塞
 */
@Slf4j
@RefreshScope
@Component("BigDataAuth")
public class BigDataAuth extends AbstractAuthFilter<BaseAuthConfig> {

    @Value("${secure.header.bigdata.secret:default-secret}")
    private String secret;

    /**
     * 构造器注入线程池
     */
    public BigDataAuth(ExecutorService executorService, CacheManager cacheManager) {
        super(BaseAuthConfig.class, executorService, cacheManager);
    }

    @Override
    protected String getSecret() {
        return secret;
    }

    /**
     * 方法级拦截逻辑
     * 仅允许 POST 请求，其它方法阻塞
     */
    @Override
    protected boolean authorizedRequest(String method) {
        Set<String> allowedMethods = Set.of("POST");
        return !allowedMethods.contains(method.toUpperCase());
    }
}
