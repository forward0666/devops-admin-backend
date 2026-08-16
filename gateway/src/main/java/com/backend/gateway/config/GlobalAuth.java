1|package com.backend.gateway.config;
2|
3|import com.backend.gateway.filter.AuthFilter;
4|import lombok.extern.slf4j.Slf4j;
5|6|import org.springframework.beans.factory.annotation.Value;
7|import org.springframework.cloud.context.config.annotation.RefreshScope;
8|import org.springframework.stereotype.Component;
9|10|11|
12|@Slf4j
13|@RefreshScope
14|@Component("global")
15|public class GlobalAuth extends AuthFilter<BaseAuthConfig> {
16|
17|    @Value("${secure.global.whitelist-paths:}")
18|    private String whitelistPaths;
19|
20|    public GlobalAuth() {
21|        super(BaseAuthConfig.class);
22|    }
23|
24|    @Override
25|    protected boolean isWhitelistedPath(String path) {
26|        if (path == null || whitelistPaths == null || whitelistPaths.isBlank()) {
27|            return false;
28|        }
29|        for (String wp : whitelistPaths.split(",")) {
30|            String trimmed = wp.trim();
31|            if (!trimmed.isEmpty() && path.startsWith(trimmed)) {
32|                return true;
33|            }
34|        }
35|        return false;
36|    }
37|}
38|