package com.backend.gateway.filter.impl;

import com.backend.gateway.config.BaseAuthConfig;
import com.backend.gateway.filter.AbstractAuthFilter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cloud.context.config.annotation.RefreshScope;
import org.springframework.stereotype.Component;

@Slf4j
@RefreshScope
@Component("GlobalAuth")
public class GlobalAuth extends AbstractAuthFilter<BaseAuthConfig> {

    @Value("${secure.header.global.secret:default-secret}")
    private String secret;

    public GlobalAuth() {
        super(BaseAuthConfig.class);
    }

    @Override
    protected String getSecret() {
        return secret;
    }
}
