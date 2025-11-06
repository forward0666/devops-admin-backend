package com.backend.gateway.filter.impl;

import com.backend.gateway.config.BaseAuthConfig;
import com.backend.gateway.filter.AbstractAuthFilter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cloud.context.config.annotation.RefreshScope;
import org.springframework.stereotype.Component;

import java.util.Set;

@Slf4j
@RefreshScope
@Component("BigDataAuth")
public class BigDataAuth extends AbstractAuthFilter<BaseAuthConfig> {

    @Value("${secure.header.bigdata.secret:default-secret}")
    private String secret;

    public BigDataAuth() {
        super(BaseAuthConfig.class);
    }

    @Override
    protected String getSecret() {
        return secret;
    }

    @Override
    protected boolean authorizedRequest(String method) {
        // 允许的方法列表
        // Set<String> allowedMethods = Set.of("POST", "PUT");
        Set<String> allowedMethods = Set.of("POST");
        // 如果请求方法不在允许列表中，就阻塞
        return !allowedMethods.contains(method.toUpperCase());
    }

}
