package com.backend.monitor;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.client.discovery.EnableDiscoveryClient;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication(
        scanBasePackages = {
                "com.backend.utils",
                "config",
                "shutdown",
                "com.backend.monitor"
        })
@EnableDiscoveryClient
@EnableScheduling
public class MonitorApplication {
    public static void main(String[] args) {
        SpringApplication.run(MonitorApplication.class, args);
    }
}