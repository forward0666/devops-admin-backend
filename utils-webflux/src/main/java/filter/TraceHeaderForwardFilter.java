package filter;

import filter.TraceIdFilter;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

/**
 * GlobalFilter: 将 Trace ID 从 Reactor Context 注入到下游请求头 X-Trace-Id 中。
 */
@Component
public class TraceHeaderForwardFilter implements GlobalFilter, Ordered {

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        // 从 Reactor Context 中获取 Trace ID
        return Mono.deferContextual(contextView -> {
            String traceId = contextView
                    .getOrEmpty(TraceIdFilter.CONTEXT_KEY_TRACE_ID)
                    .map(Object::toString)
                    .orElse(null);

            if (traceId != null) {
                // 核心步骤：修改请求，添加 X-Trace-Id Header
                ServerHttpRequest mutatedRequest = exchange.getRequest().mutate()
                        .header(TraceIdFilter.TRACE_ID_HEADER_X_TRACE_ID, traceId)
                        .build();

                // 继续过滤链，使用新的请求对象
                return chain.filter(exchange.mutate().request(mutatedRequest).build());
            }

            // Context 中没有 Trace ID，直接继续
            return chain.filter(exchange);
        });
    }

    // 确保该过滤器在路由之前执行，但要在 TraceIdFilter 之后执行
    @Override
    public int getOrder() {
        return 0; // 假设 TraceIdFilter 的 Order 是 -100，这个值在它之后即可
    }
}