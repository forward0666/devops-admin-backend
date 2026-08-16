package com.backend.agent;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.client.discovery.EnableDiscoveryClient;

@SpringBootApplication(
        scanBasePackages = {
                "com.backend.utils",
                "config",
                "shutdown",
                "com.backend.agent"
        })
@EnableDiscoveryClient
public class AgentApplication {
    public static void main(String[] args) {
        SpringApplication.run(AgentApplication.class, args);
    }
}