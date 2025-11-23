package webflux;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule; // 务必引入 jackson-datatype-jsr310 依赖
import lombok.extern.slf4j.Slf4j;
import org.springframework.cloud.gateway.route.Route;
import org.springframework.cloud.gateway.support.ServerWebExchangeUtils;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.core.io.buffer.DataBufferFactory;
import org.springframework.http.HttpCookie;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Optional;

/**
 * WebExchange 全能工具类
 * <p>
 * 作用：封装 ServerWebExchange 的常用操作，包括获取请求信息、路由信息、以及写出 JSON 响应。
 * 这是一个底层工具类，通常被上层的 HttpResponseUtils 调用。
 */
@Slf4j
public class WebExchangeUtils {

    // 静态 ObjectMapper，用于手动序列化
    private static final ObjectMapper MAPPER = new ObjectMapper();

    static {
        // 【关键优化】注册 Java Time 模块，防止 LocalDateTime 序列化报错
        MAPPER.registerModule(new JavaTimeModule());
        // 可选：设置遇到空字段不序列化
        // MAPPER.setSerializationInclusion(com.fasterxml.jackson.annotation.JsonInclude.Include.NON_NULL);
    }

    private WebExchangeUtils() {}

    // ==================== 1. 读：基础请求信息 ====================

    /** 获取请求头值（忽略大小写，安全判空） */
    public static String getHeader(ServerWebExchange exchange, String headerName) {
        if (exchange == null || headerName == null) {
            return null;
        }
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

    /** 获取客户端 IP (支持代理穿透) */
    public static String getClientIp(ServerWebExchange exchange) {
        List<String> headers = List.of("CF-Connecting-IP", "X-Forwarded-For", "X-Real-IP");
        for (String header : headers) {
            String value = getHeader(exchange, header);
            if (value != null && !value.isBlank()) {
                return value.split(",")[0].trim();
            }
        }
        InetSocketAddress remoteAddress = exchange.getRequest().getRemoteAddress();
        return remoteAddress != null ? remoteAddress.getAddress().getHostAddress() : "UNKNOWN";
    }

    // ==================== 2. 读：参数与属性 ====================

    /** 获取 Query 参数 */
    public static String getQueryParam(ServerWebExchange exchange, String name) {
        return exchange.getRequest().getQueryParams().getFirst(name);
    }

    /** 获取 Cookie */
    public static String getCookie(ServerWebExchange exchange, String name) {
        HttpCookie cookie = exchange.getRequest().getCookies().getFirst(name);
        return cookie != null ? cookie.getValue() : null;
    }

    /** 获取路由 ID (Gateway 专用) */
    public static String getRouteId(ServerWebExchange exchange) {
        Route route = exchange.getAttribute(ServerWebExchangeUtils.GATEWAY_ROUTE_ATTR);
        return route != null ? route.getId() : "unknown";
    }

    /** 获取 Attribute (带默认值) */
    public static <T> T getAttribute(ServerWebExchange exchange, String key, T defaultValue) {
        T value = exchange.getAttribute(key);
        return value != null ? value : defaultValue;
    }

    /** 获取响应对象 */
    public static ServerHttpResponse getResponse(ServerWebExchange exchange) {
        return exchange.getResponse();
    }

    // ==================== 3. 写：响应处理 ====================

    /**
     * 将 ResponseEntity 写入响应并终止请求链 (底层实现)
     */
    public static Mono<Void> responseJson(ServerWebExchange exchange, ResponseEntity<?> entity) {
        ServerHttpResponse response = exchange.getResponse();

        // 1. 设置状态码
        response.setStatusCode(entity.getStatusCode());

        // 2. 复制 Header
        Optional.ofNullable(entity.getHeaders())
                .ifPresent(headers -> response.getHeaders().addAll(headers));

        // 3. 强制设置 Content-Type 为 JSON
        response.getHeaders().setContentType(MediaType.APPLICATION_JSON);

        // 4. 序列化 Body
        Object body = entity.getBody();
        if (body == null) {
            return response.setComplete();
        }

        DataBufferFactory bufferFactory = response.bufferFactory();
        DataBuffer buffer;

        try {
            // writeValueAsBytes 性能略优于 writeValueAsString
            byte[] bytes = MAPPER.writeValueAsBytes(body);
            buffer = bufferFactory.wrap(bytes);
        } catch (JsonProcessingException e) {
            log.error("JSON 序列化失败: {}", e.getMessage(), e);
            response.setStatusCode(org.springframework.http.HttpStatus.INTERNAL_SERVER_ERROR);
            String errorJson = "{\"status\":\"error\",\"code\":500,\"message\":\"Internal Serialization Error\"}";
            buffer = bufferFactory.wrap(errorJson.getBytes(StandardCharsets.UTF_8));
        }

        return response.writeWith(Mono.just(buffer));
    }
}