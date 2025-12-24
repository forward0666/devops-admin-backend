package com.admin.security;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.client.discovery.EnableDiscoveryClient;
//import org.springframework.cloud.openfeign.EnableFeignClients;


@EnableDiscoveryClient
//@EnableFeignClients

@SpringBootApplication(
        scanBasePackages = {
                "com.admin.security", // 主工程包
                "shutdown",
                "config",
                "monitor",
                "filter"
        }
        ,
        exclude = {
                org.springframework.boot.autoconfigure.data.redis.RedisAutoConfiguration.class,
                org.springframework.boot.autoconfigure.data.mongo.MongoDataAutoConfiguration.class,
                org.springframework.boot.autoconfigure.data.mongo.MongoRepositoriesAutoConfiguration.class,
                org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration.class
        }
)
public class SecurityApplication {

    public static void main(String[] args) {
        SpringApplication.run(SecurityApplication.class, args);
    }
}