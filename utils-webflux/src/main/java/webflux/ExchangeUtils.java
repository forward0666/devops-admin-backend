package webflux;

import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.cloud.gateway.route.Route;
import org.springframework.cloud.gateway.support.ServerWebExchangeUtils;

public class ExchangeUtils {

    private ExchangeUtils() {}

    /** 获取请求头值（忽略大小写） */
    public static String getHeader(ServerWebExchange exchange, String headerName) {
        return exchange.getRequest().getHeaders().getFirst(headerName);
    }

    /** 获取请求方法 */
    public static String getMethod(ServerWebExchange exchange) {
        return exchange.getRequest().getMethod().name();
    }

    /** 获取请求路径 */
    public static String getPath(ServerWebExchange exchange) {
        return exchange.getRequest().getPath().toString();
    }

    /** 获取路由ID（若不存在返回 unknown） */
    public static String getRouteId(ServerWebExchange exchange) {
        Route route = exchange.getAttribute(ServerWebExchangeUtils.GATEWAY_ROUTE_ATTR);
        return route != null ? route.getId() : "unknown";
    }

    /** 获取响应对象 */
    public static ServerHttpResponse getResponse(ServerWebExchange exchange) {
        return exchange.getResponse();
    }
}
