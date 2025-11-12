package network;

import org.springframework.web.server.ServerWebExchange;

import java.net.InetSocketAddress;
import java.util.List;
import java.util.UUID;

/**
 * 网络请求头工具类
 * 提供获取客户端真实 IP 和任意请求头的功能
 */
public class ClientHeaderUtils {

    private ClientHeaderUtils() {
        // 工具类禁止实例化
    }

    /**
     * 获取客户端真实 IP 地址（多级代理支持）
     */
    public static String getClientIp(ServerWebExchange exchange) {
        List<String> headers = List.of(
                "CF-Connecting-IP"
//                "X-Forwarded-For",
//                "X-Real-IP"
        );

        for (String h : headers) {
            String value = exchange.getRequest().getHeaders().getFirst(h);
            if (value != null && !value.isBlank()) {
                // X-Forwarded-For 可能有多个 IP，用第一个
                return value.split(",")[0].trim();
            }
        }

        InetSocketAddress addr = exchange.getRequest().getRemoteAddress();
        return addr != null ? addr.getAddress().getHostAddress() : "UNKNOWN";
    }

    /**
     * 获取请求头（忽略大小写）
     */
    public static String getClientHeader(ServerWebExchange exchange, String headerName) {
        if (exchange == null || headerName == null) {
            return null;
        }
        return exchange.getRequest().getHeaders().getFirst(headerName);
    }

    /**
     * 获取全链路 traceId：
     * 优先顺序：
     * 1. CF-RAY 请求头
     * 2. X-Trace-Id 请求头
     * 3. 如果都没有则生成 UUID
     */
    public static String getTraceId(ServerWebExchange exchange) {
        String cfRay = getClientHeader(exchange, "CF-RAY");
        if (cfRay != null && !cfRay.isBlank()) {
            return cfRay;
        }

        String xTraceId = getClientHeader(exchange, "X-Trace-Id");
        if (xTraceId != null && !xTraceId.isBlank()) {
            return xTraceId;
        }
        return UUID.randomUUID().toString();
    }

}
