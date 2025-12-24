package com.admin.manage;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.client.discovery.EnableDiscoveryClient;
import org.springframework.cloud.context.config.annotation.RefreshScope;
import org.springframework.cloud.openfeign.EnableFeignClients;

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
//        ,
//        exclude = {
//                org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration.class
//        }
)
public class ManageApplication {

    public static void main(String[] args) {
        SpringApplication.run(ManageApplication.class, args);
    }

}
