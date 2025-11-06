package com.backend.gateway;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.client.discovery.EnableDiscoveryClient;
import org.springframework.cloud.context.config.annotation.RefreshScope;
import org.springframework.cloud.openfeign.EnableFeignClients;

/**
 * 网关服务主应用类
 *
 * 功能说明：
 * 1. 作为API网关的入口点，负责请求路由、过滤和负载均衡
 * 2. 集成服务发现、配置刷新和Feign客户端功能
 *
 * 注解说明：
 * @SpringBootApplication - 标识为Spring Boot应用，包含自动配置、组件扫描等功能
 * @EnableDiscoveryClient - 启用服务发现功能，与Nacos等服务注册中心集成
 * @RefreshScope - 启用配置动态刷新，支持热更新配置而不重启应用
 * @EnableFeignClients - 启用Feign声明式HTTP客户端，用于服务间调用
 *
 * 启动流程：
 * 1. 加载Spring Boot自动配置
 * 2. 连接到Nacos配置中心获取动态配置
 * 3. 注册到Nacos服务发现中心
 * 4. 初始化网关路由和过滤器配置
 * 5. 启动Netty服务器监听HTTP请求
 */
@SpringBootApplication
@EnableDiscoveryClient
@RefreshScope
@EnableFeignClients
public class GatewayApplication {

    /**
     * 应用主入口方法
     *
     * @param args 命令行参数
     * 启动过程：
     * 1. 初始化Spring应用上下文
     * 2. 加载所有配置的Bean和组件
     * 3. 启动内嵌的Web服务器（默认Netty）
     * 4. 开始接收和处理HTTP请求
     */
    public static void main(String[] args) {
        SpringApplication.run(GatewayApplication.class, args);
    }

}
