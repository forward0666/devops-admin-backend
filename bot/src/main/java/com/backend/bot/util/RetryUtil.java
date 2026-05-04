package com.backend.bot.util;

import org.springframework.http.HttpStatus;
import org.springframework.web.reactive.function.client.WebClientException;
import org.springframework.web.reactive.function.client.WebClientResponseException;
import reactor.util.retry.Retry;

import java.time.Duration;
import java.util.function.Predicate;

public class RetryUtil {

    /**
     * 定义一个 Predicate 来判断哪些异常可以重试。
     * 1. WebClientException: 这是一个广义的网络/客户端错误，例如连接超时、DNS查找失败等。
     * 2. WebClientResponseException (5xx): 服务器错误，通常是临时的（如 500, 503, 504）。
     * Telegram API 也可能使用 429 Too Many Requests，如果想重试 429，也应包含进来。
     */
    private static final Predicate<? super Throwable> RETRYABLE_EXCEPTIONS = throwable -> {
        // 匹配 WebClient 层的网络/IO 错误
        if (throwable instanceof WebClientException) {
            // 排除 4xx 客户端错误 (WebClientResponseException 的子类)
            if (throwable instanceof WebClientResponseException responseException) {
                // 重试 5xx 服务器错误
                if (responseException.getStatusCode().is5xxServerError()) {
                    return true;
                }
                // 【可选】重试 429 Too Many Requests
                if (responseException.getStatusCode() == HttpStatus.TOO_MANY_REQUESTS) {
                    return true;
                }
                // 所有其他 4xx 错误 (400, 401, 404, 403, etc.) 不重试
                return false;
            }
            // 所有非 WebClientResponseException 的 WebClientException (如连接错误) 都重试
            return true;
        }
        // 其他非 WebClient 异常不重试
        return false;
    };

    /**
     * 创建一个应用指数退避重试策略的 Reactor Retry 规范。
     *
     * @param maxAttempts 最大重试次数 (例如 3 次)
     * @param minBackoff 初始退避时间 (例如 100 毫秒)
     * @return 配置好的 Retry 规范
     */
    public static Retry exponentialBackoff(int maxAttempts, Duration minBackoff) {
        return Retry.backoff(maxAttempts, minBackoff)
                .filter(RETRYABLE_EXCEPTIONS)
                // 【重要】当超过最大尝试次数时，抛出原始异常
                .onRetryExhaustedThrow((retryBackoffSpec, retrySignal) -> retrySignal.failure());
    }
}