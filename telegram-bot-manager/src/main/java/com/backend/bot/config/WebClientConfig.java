package com.backend.bot.config;

import io.netty.channel.ChannelOption;
import io.netty.handler.timeout.ReadTimeoutHandler;
import io.netty.handler.timeout.WriteTimeoutHandler;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.reactive.ReactorClientHttpConnector;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.netty.http.client.HttpClient;

import java.time.Duration;
import java.util.concurrent.TimeUnit;

/**
 * 配置 WebClient 实例，设置连接和请求超时。
 * 默认配置 Telegram API Base URL 和 10 秒超时。
 */
@Configuration
public class WebClientConfig {

    @Value("${telegram.api-base-url:https://api.telegram.org}")
    private String telegramApiBaseUrl;

    /**
     * 配置 WebClient Bean。
     * 关键是设置底层 HttpClient 的超时策略。
     */
    @Bean
    public WebClient telegramWebClient() {
        // 设置连接超时时间 (5000ms)
        HttpClient httpClient = HttpClient.create()
                .option(ChannelOption.CONNECT_TIMEOUT_MILLIS, 5000)
                .responseTimeout(Duration.ofSeconds(10)) // 设置完整的响应超时（从发送请求到接收完整响应）
                .doOnConnected(conn -> conn
                        .addHandlerLast(new ReadTimeoutHandler(10, TimeUnit.SECONDS)) // 读超时 10s
                        .addHandlerLast(new WriteTimeoutHandler(5, TimeUnit.SECONDS))); // 写超时 5s

        return WebClient.builder()
                .baseUrl(telegramApiBaseUrl)
                .clientConnector(new ReactorClientHttpConnector(httpClient))
                .build();
    }
}