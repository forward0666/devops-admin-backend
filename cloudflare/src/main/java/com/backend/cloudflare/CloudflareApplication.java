package com.backend.cloudflare;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.client.discovery.EnableDiscoveryClient;

@SpringBootApplication(
        scanBasePackages = {
                "com.backend.utils",
                "com.backend.cloudflare"
        })
@EnableDiscoveryClient
public class CloudflareApplication {
    public static void main(String[] args) {
        SpringApplication.run(CloudflareApplication.class, args);
    }
}