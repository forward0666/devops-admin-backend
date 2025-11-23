package filter;

import network.TraceIdUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebFilter;
import org.springframework.web.server.WebFilterChain;
import reactor.core.publisher.Mono;
import reactor.util.context.Context;

@Component
@Order(-100) // 提高优先级，确保最早执行
public class TraceIdFilter implements WebFilter {

    private static final Logger log = LoggerFactory.getLogger(TraceIdFilter.class);

    // 请求头常量
    public static final String TRACE_ID_HEADER_CF_RAY = "CF-RAY";
    public static final String TRACE_ID_HEADER_X_TRACE_ID = "X-Trace-Id";

    // Reactor Context Key
    public static final String CONTEXT_KEY_TRACE_ID = "traceId";

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, WebFilterChain chain) {

        // 1. 手动获取原始头部值用于日志打印
        String cfRay = exchange.getRequest().getHeaders().getFirst(TRACE_ID_HEADER_CF_RAY);
        String xTraceId = exchange.getRequest().getHeaders().getFirst(TRACE_ID_HEADER_X_TRACE_ID);

        // 2. 使用 TraceIdUtils 提取最终的 Trace ID
        String traceId = TraceIdUtils.getTraceId(exchange);

        // 3. 打印详细的接收和最终确定的 Trace ID 日志
        log.info("✅ Received traceId from request headers: CF-RAY='{}', X-Trace-Id='{}', final traceId='{}'",
                cfRay != null ? cfRay : "",
                xTraceId != null ? xTraceId : "",
                traceId);

        // 4. 将 Trace ID 放入 Reactor Context
        return chain.filter(exchange)
                // 5. 将 Trace ID 设置到 MDC (可选但推荐，用于主线程日志)
                .doOnEach(signal -> {
                    if (signal.isOnNext() || signal.isOnComplete() || signal.isOnError()) {
                        TraceIdUtils.setTraceId(traceId);
                    }
                })
                .doFinally(signalType -> TraceIdUtils.clearMdc()) // 清理 MDC
                // 6. 将新的 Context 注入到下游的 Mono 中
                .contextWrite(Context.of(CONTEXT_KEY_TRACE_ID, traceId));
    }

    // 辅助方法保持不变

    /**
     * 【重要辅助方法】 (保持不变)
     * 用于在 WebFlux 业务代码（Controller/Service）中获取当前请求的 TraceId。
     */
    public static Mono<String> getCurrentTraceId() {
        return Mono.deferContextual(contextView -> {
            if (contextView.hasKey(CONTEXT_KEY_TRACE_ID)) {
                return Mono.just(contextView.get(CONTEXT_KEY_TRACE_ID).toString());
            }
            return Mono.empty();
        });
    }
}