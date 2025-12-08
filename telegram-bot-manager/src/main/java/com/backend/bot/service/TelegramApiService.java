package com.backend.bot.service;

import com.backend.bot.util.RetryUtil;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;
import reactor.util.retry.Retry;

import java.time.Duration;

@Service
public class TelegramApiService {

    private final WebClient webClient;
    private final Retry telegramRetryPolicy;

    public TelegramApiService(WebClient telegramWebClient) {
        this.webClient = telegramWebClient;
        // 配置 Telegram API 的重试策略：最多重试 3 次，初始退避 100ms
        this.telegramRetryPolicy = RetryUtil.exponentialBackoff(3, Duration.ofMillis(100));
    }

    /**
     * 示例：调用 Telegram API 的 getMe 方法
     * @return 包含用户信息 Mono<String>
     */
    public Mono<String> getMe() {
        return webClient.get()
                .uri("/bot{token}/getMe") // 假设 token 已经被处理或作为 PathVariable/Header
                .retrieve()
                .bodyToMono(String.class)
                // 【核心】应用指数退避重试策略
                .retryWhen(telegramRetryPolicy);
    }

    // 其他 API 调用方法...
}