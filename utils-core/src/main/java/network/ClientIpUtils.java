package network;

import java.net.InetSocketAddress;
import java.util.List;
import org.springframework.web.server.ServerWebExchange;

/**
 * 网络工具类 - 用于解析客户端真实IP地址
 */
public class ClientIpUtils {

    private ClientIpUtils() {
        // 工具类禁止实例化
    }

    /**
     * 获取客户端真实 IP 地址（多级代理支持）
     */
    public static String getClientIp(ServerWebExchange exchange) {
        List<String> headers = List.of(
                "CF-Connecting-IP",
                "X-Forwarded-For",
                "X-Real-IP"
        );

        for (String h : headers) {
            String value = exchange.getRequest().getHeaders().getFirst(h);
            if (value != null && !value.isBlank()) {
                return value.split(",")[0].trim();
            }
        }

        InetSocketAddress addr = exchange.getRequest().getRemoteAddress();
        return addr != null ? addr.getAddress().getHostAddress() : "UNKNOWN";
    }
}
