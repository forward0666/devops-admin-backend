package com.backend.gateway.config;

/**
 * 通用认证过滤器配置
 */
public class BaseAuthConfig {
    private boolean enabled = true;

    public boolean isEnabled() { return enabled; }
    public void setEnabled(boolean enabled) { this.enabled = enabled; }
}
