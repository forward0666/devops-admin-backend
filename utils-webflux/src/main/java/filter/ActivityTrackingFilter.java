package filter;

import exception.AggressiveTimeoutException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebFilter;
import org.springframework.web.server.WebFilterChain;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.util.Collections;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeoutException;
import java.util.stream.Collectors;

import static network.TraceIdUtils.getTraceId;
import static webflux.WebExchangeUtils.*;

/**
 * 活动追踪过滤器：用于记录请求活动。
 * 仅对 telegram-bot-manager 服务应用 2 秒超时限制，对 Webhook 接口进行特殊处理（超时返回 200 OK）。
 * 其他服务不设置超时限制。
 * 使用 JVM Shutdown Hook 机制，确保在应用强制关闭时，仍能输出当前活跃的请求列表。
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
@Slf4j
public class ActivityTrackingFilter implements WebFilter, InitializingBean {

    // Webhook 路径前缀，用于特殊处理
    private static final String WEBHOOK_PATH_PREFIX = "/bot/callback/";
    
    // Telegram Bot Manager 服务名称
    private static final String TELEGRAM_BOT_MANAGER_ROUTE = "telegram-bot-manager";

    // 仅对 telegram-bot-manager 应用 2 秒超时
    private static final Duration REQUEST_TIMEOUT = Duration.ofSeconds(2);

    // 存储当前活跃请求的标识符
    private final Set<String> activeRequests = Collections.newSetFromMap(new ConcurrentHashMap<>());

    @Override
    public void afterPropertiesSet() throws Exception {
        // 在 Bean 初始化后，注册 JVM Shutdown Hook
        Runtime.getRuntime().addShutdownHook(new Thread(this::logActiveRequestsOnShutdown, "ActiveRequestShutdownLogger"));
        log.info("✅ JVM Shutdown Hook registered for ActiveRequest logging.");
    }

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, WebFilterChain chain) {
        String path = exchange.getRequest().getURI().getPath();
        boolean isWebhook = path.startsWith(WEBHOOK_PATH_PREFIX);
        String requestId = createRequestId(exchange);
        String routeId = getRouteId(exchange); // 获取路由 ID
        boolean isTelegramBotManager = TELEGRAM_BOT_MANAGER_ROUTE.equals(routeId); // 判断是否为 telegram-bot-manager 服务

        activeRequests.add(requestId);

        log.info("[traceId={}]💬 Request started: {}", getTraceId(exchange), requestId);

        // 根据服务决定是否应用超时
        Mono<Void> filterChain = chain.filter(exchange);
        
        // 仅对 telegram-bot-manager 服务应用 2 秒超时
        if (isTelegramBotManager) {
            filterChain = filterChain.timeout(REQUEST_TIMEOUT);
        }
        
        return filterChain
                .doFinally(signalType -> {
                    // 1. 移除记录
                    activeRequests.remove(requestId);

                    // 2. 增强日志：记录请求的完成/取消/错误状态
                    String status = switch (signalType) {
                        case ON_COMPLETE -> "Completed (Success)";
                        case ON_ERROR -> "Completed (Error)";
                        case CANCEL -> "Cancelled (Client Disconnect)";
                        default -> "Finished (Unknown Signal)";
                    };

                    // 使用 INFO 级别记录请求的完成，便于追踪
                    log.info("[traceId={}]👌 Request finished: {} | Status: {}", getTraceId(exchange), requestId, status);
                })
                // 2. 统一处理所有超时相关的异常 (TimeoutException 和 AggressiveTimeoutException)
                // 此处捕获 TimeoutException，并确保 Webhook 返回 200 OK
                .onErrorResume(TimeoutException.class, ex -> {
                    // 只有 telegram-bot-manager 服务才会触发超时异常
                    if (isTelegramBotManager) {
                        if (isWebhook) {
                            // Webhook 超时：记录警告，返回 200 OK，防止 Telegram 重试
                            log.warn("🚨 Aggressive timeout triggered for Webhook ({}s) via Exception: {}",
                                    REQUEST_TIMEOUT.getSeconds(), path);
                            return handleWebhookTimeout(exchange);
                        }

                        // 普通 API 超时：重新抛出 AggressiveTimeoutException，由 GlobalExceptionHandler 捕获并返回 504
                        // 即使 ex 是标准的 TimeoutException，我们也将其包装以统一处理
                        return Mono.error(new AggressiveTimeoutException(
                                "API request timed out after " + REQUEST_TIMEOUT.getSeconds() + " seconds."
                        ));
                    }
                    
                    // 如果不是 telegram-bot-manager 服务，继续传播原始异常
                    return Mono.error(ex);
                })
                // 3. 捕获在处理链中其他地方手动抛出的 AggressiveTimeoutException
                .onErrorResume(AggressiveTimeoutException.class, ex -> {
                    // 只有 telegram-bot-manager 服务才会处理 AggressiveTimeoutException
                    if (isTelegramBotManager) {
                        if (isWebhook) {
                            // Webhook 路径：捕获并返回 200 OK
                            log.error("⚠️ Caught AggressiveTimeoutException in Webhook path, returning 200 OK: {}", ex.getMessage());
                            return handleWebhookTimeout(exchange);
                        }

                        // 普通 API 路径：继续抛出，由 GlobalExceptionHandler 捕获并返回 504
                        return Mono.error(ex);
                    }
                    
                    // 如果不是 telegram-bot-manager 服务，继续传播原始异常
                    return Mono.error(ex);
                });
    }

    /**
     * 优雅地写入一h个 200 OK 响应，这是 Webook 超时的标准处理方式。
     */
    private Mono<Void> handleWebhookTimeout(ServerWebExchange exchange) {
        // 如果响应未提交，设置状态码为 200 OK，并完成响应。
        if (!exchange.getResponse().isCommitted()) {
            exchange.getResponse().setStatusCode(HttpStatus.OK);
            // 写入响应体（可选，但通常 Webhook 不需要）
            return exchange.getResponse().setComplete();
        }
        // 如果已提交，则无法修改，返回一个空 Mono
        return Mono.empty();
    }

    /**
     * 构建一个能代表请求的字符串，例如：Method + Path + 唯一ID
     */
    private String createRequestId(ServerWebExchange exchange) {
        return String.format("[%s] %s from %s | ID: %s",
                getMethod(exchange),
                getPath(exchange),
                getClientIp(exchange),
                getId(exchange)
        );
    }

    /**
     * 这是 JVM Hook 执行的方法。它使用 System.err 保证输出。
     */
    public void logActiveRequestsOnShutdown() {
        // 创建一个快照，在 JVM 终止前保证数据的一致性
        Set<String> snapshot = activeRequests.stream().collect(Collectors.toSet());

        if (!snapshot.isEmpty()) {
            // 使用 System.err.println 强制输出，这是最可靠的方式
            System.err.println("\n\n****************************************************************************************");
            System.err.println("!!! JVM SHUTDOWN HOOK ACTIVATED !!!");
            System.err.printf("!!! Application Shutdown: %d active requests did not complete gracefully!%n", snapshot.size());

            snapshot.forEach(req -> {
                System.err.println("   - ACTIVE REQUEST IDENTIFIED: " + req);
            });

            System.err.println("****************************************************************************************\n");
        } else {
            // 正常情况下，这里不会被看到，但逻辑上是完整的
            System.err.println("✅ JVM Hook: No active requests found during shutdown.");
        }
    }
}