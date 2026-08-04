package com.backend.cloudflare.config;

import com.xxl.job.core.executor.impl.XxlJobSpringExecutor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Slf4j
@Configuration
public class XxlJobConfig {

    @Value("${xxl.job.admin.addresses:http://127.0.0.1:8080/xxl-job-admin}")
    private String adminAddresses;

    @Value("${xxl.job.executor.appname:cloudflare-service}")
    private String appName;

    @Value("${xxl.job.executor.port:9998}")
    private int port;

    @Value("${xxl.job.accessToken:default_token}")
    private String accessToken;

    @Bean
    public XxlJobSpringExecutor xxlJobExecutor() {
        log.info(">>>>>>>>>>> xxl-job config init, adminAddresses: {}, appName: {}", adminAddresses, appName);
        var executor = new XxlJobSpringExecutor();
        executor.setAdminAddresses(adminAddresses);
        executor.setAppname(appName);
        executor.setPort(port);
        executor.setAccessToken(accessToken);
        executor.setLogPath("/tmp/xxl-job/cloudflare/logs");
        executor.setLogRetentionDays(7);
        return executor;
    }
}