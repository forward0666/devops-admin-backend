package com.backend.domain;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.client.discovery.EnableDiscoveryClient;

@SpringBootApplication(
        scanBasePackages = {
                "com.backend.utils",
                "config",
                "shutdown",
                "com.backend.domain"
        })
@EnableDiscoveryClient
public class DomainApplication {
    public static void main(String[] args) {
        SpringApplication.run(DomainApplication.class, args);
    }
}