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

        // **优化 1: 使用 TraceIdUtils 提取 Trace ID**
        String traceId = TraceIdUtils.getTraceId(exchange);

        log.info("✅ WebFlux traceId initialized: final traceId='{}'", traceId);

        // 2. 将 TraceId 放入 Reactor Context
        return chain.filter(exchange)
                // 3. 将 Trace ID 设置到 MDC (可选但推荐，用于主线程日志)
                .doOnEach(signal -> {
                    if (signal.isOnNext() || signal.isOnComplete() || signal.isOnError()) {
                        TraceIdUtils.setTraceId(traceId);
                    }
                })
                .doFinally(signalType -> TraceIdUtils.clearMdc()) // 清理 MDC
                // 4. 将新的 Context 注入到下游的 Mono 中
                .contextWrite(Context.of(CONTEXT_KEY_TRACE_ID, traceId));
    }

    // **优化 2: 移除冗余的 getTraceId 方法，已委托给 TraceIdUtils**
    /* private String getTraceId(String cfRay, String xTraceId) { ... }
     */

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