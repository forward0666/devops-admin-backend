package com.backend.bot.config;

import io.netty.channel.ChannelOption;
import io.netty.handler.timeout.ReadTimeoutHandler;
import io.netty.handler.timeout.WriteTimeoutHandler;
import lombok.Data;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.reactive.ReactorClientHttpConnector;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.netty.http.client.HttpClient;

import java.time.Duration;
import java.util.concurrent.TimeUnit;

/**
 * WebClient HTTP 客户端配置类
 * 
 * 该类负责配置响应式 HTTP 客户端 WebClient，用于与 Telegram API 进行通信。
 * WebClient 是 Spring WebFlux 提供的非阻塞、响应式 HTTP 客户端，
 * 替代了传统 Spring MVC 中的 RestTemplate。
 * 
 * 主要配置内容：
 * 1. 连接超时设置 - 防止长时间等待连接建立
 * 2. 读写超时设置 - 控制数据传输时间
 * 3. 响应超时设置 - 限制整个请求-响应周期
 * 4. 基础 URL 配置 - Telegram API 地址
 * 
 * @author Backend Team
 * @version 1.0.0
 */
@Configuration                              // Spring 配置类注解
public class WebClientConfig {

    /**
     * Telegram API 基础 URL
     * 
     * 从配置文件中读取，默认值为 https://api.telegram.org
     * 使用 @Value 注解实现配置值的注入，支持默认值设置
     */
    @Value("${telegram.api-base-url:https://api.telegram.org}")
    private String telegramApiBaseUrl;

    /**
     * 配置 Telegram API 专用的 WebClient Bean
     * 
     * 创建并配置一个专门用于与 Telegram API 通信的 WebClient 实例。
     * WebClient 基于响应式编程模型，使用 Netty 作为底层 HTTP 客户端，
     * 提供非阻塞的 HTTP 请求处理能力。
     * 
     * 超时配置说明：
     * - 连接超时：5秒 - TCP 连接建立的最大等待时间
     * - 响应超时：10秒 - 从发送请求到接收完整响应的总时间
     * - 读超时：10秒 - 连接建立后读取数据的最大等待时间
     * - 写超时：5秒 - 连接建立后发送数据的最大等待时间
     * 
     * @return 配置好的 WebClient 实例，专用于 Telegram API 调用
     */
    @Bean
    public WebClient telegramWebClient() {
        // 创建并配置 Netty HttpClient，设置各种超时参数
        HttpClient httpClient = HttpClient.create()
                // 设置连接超时时间为 5000 毫秒（5秒）
                .option(ChannelOption.CONNECT_TIMEOUT_MILLIS, 5000)
                // 设置完整响应超时：从发送请求到接收完整响应的总时间为 10 秒
                .responseTimeout(Duration.ofSeconds(10))
//                .在连接建立后配置读写超时处理器
                .doOnConnected(conn -> conn
                        // 添加读超时处理器：连接建立后，10秒内无数据接收则超时
                        .addHandlerLast(new ReadTimeoutHandler(10, TimeUnit.SECONDS))
                        // 添加写超时处理器：连接建立后，5秒内无法发送数据则超时
                        .addHandlerLast(new WriteTimeoutHandler(5, TimeUnit.SECONDS)));

        // 使用构建器模式创建 WebClient 实例
        return WebClient.builder()
                // 设置基础 URL，所有相对路径请求都会基于此 URL
                .baseUrl(telegramApiBaseUrl)
                // 使用自定义配置的 HttpClient 作为连接器
                .clientConnector(new ReactorClientHttpConnector(httpClient))
                // 构建 WebClient 实例
                .build();
    }
}

