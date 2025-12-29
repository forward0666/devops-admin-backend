package com.backend.security;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.client.discovery.EnableDiscoveryClient;

// 使用Java 21的unnamed variables and var特性
@EnableDiscoveryClient
@SpringBootApplication(
        scanBasePackages = {
                "com.backend.security", // 主工程包
                "shutdown",
                "config",
                "monitor",
                "filter"
        }
)
public class SecurityApplication {

    public static void main(String[] args) {
        var app = new SpringApplication(SecurityApplication.class);
        app.run(args);
    }
}