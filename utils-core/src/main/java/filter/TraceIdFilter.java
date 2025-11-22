package filter;

import jakarta.servlet.*;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.UUID;

@Component
@Order(1) // 确保在其他 filter 之前执行
@Slf4j
public class TraceIdFilter implements Filter {

    public static final String TRACE_ID_HEADER_CF_RAY = "CF-RAY";
    public static final String TRACE_ID_HEADER_X_TRACE_ID = "X-Trace-Id";
    public static final String MDC_KEY_TRACE_ID = "traceId";

    @Override
    public void doFilter(ServletRequest servletRequest, ServletResponse servletResponse, FilterChain filterChain)
            throws IOException, ServletException {

        // 1. 从 HttpServletRequest 中提取 Headers
        HttpServletRequest request = (HttpServletRequest) servletRequest;
        String cfRay = request.getHeader(TRACE_ID_HEADER_CF_RAY);
        String xTraceId = request.getHeader(TRACE_ID_HEADER_X_TRACE_ID);

        // 2. 实现 TraceId 提取逻辑
        String traceId = getTraceId(cfRay, xTraceId);

        // 3. 放入 MDC
        MDC.put(MDC_KEY_TRACE_ID, traceId);

        // 4. (可选) 打印日志，确认 TraceId 提取成功
        // 注意：这里只打印一次即可，无需在 Controller 中重复打印
//        log.info("✅ Received traceId from request headers: CF-RAY='{}', X-Trace-Id='{}', final traceId='{}'", cfRay, xTraceId, traceId);

        try {
            // 5. 调用链中下一个 Filter 或 Controller
            filterChain.doFilter(servletRequest, servletResponse);
        } finally {
            // 6. 务必在请求结束时清除 MDC，防止内存泄漏和线程污染
            MDC.remove(MDC_KEY_TRACE_ID);
        }
    }

    private String getTraceId(String cfRay, String xTraceId) {
        if (cfRay != null && !cfRay.isBlank()) {
            return cfRay;
        }
        if (xTraceId != null && !xTraceId.isBlank()) {
            return xTraceId;
        }
        // 确保使用 toString()，否则 UUID 实例本身可能在日志中显示不佳
        return UUID.randomUUID().toString();
    }
}