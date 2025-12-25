package com.admin.manage;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.client.discovery.EnableDiscoveryClient;
import org.springframework.cloud.context.config.annotation.RefreshScope;
import org.springframework.cloud.openfeign.EnableFeignClients;

// 使用Java 21的特性，简化代码
@EnableDiscoveryClient
@RefreshScope
@EnableFeignClients

@SpringBootApplication(
        scanBasePackages = {
                "com.admin.manage", // 主工程包
                "shutdown",
                "config",
                "monitor",
                "filter"
        }
)
public class ManageApplication {

    public static void main(String[] args) {
        // 使用var关键字和unnamed variables特性
        var app = new SpringApplication(ManageApplication.class);
        app.run(args);
    }
}
