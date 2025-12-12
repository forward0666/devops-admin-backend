package network;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;
import webflux.WebExchangeUtils; // 假设这个类存在

import java.util.HashMap;
import java.util.Map;

/**
 * HTTP 响应构建工具类
 * <p>
 * 1. 提供统一的 JSON 结构封装：{status, code, message, data}
 * 2. 提供直接写入 Response 流的快捷方法 (用于 Filter/Gateway)
 */
public class HttpResponseUtils {

    private HttpResponseUtils() {}

    /**
     * 核心构建逻辑：统一响应结构
     */
    private static Map<String, Object> buildResponse(HttpStatus status, String msg, Object data) {
        Map<String, Object> result = new HashMap<>();
        result.put("status", status.is2xxSuccessful() ? "ok" : "error");
        result.put("code", status.value());
        result.put("message", msg != null ? msg : status.getReasonPhrase());
        if (data != null) {
            // 注意：这里将业务数据放在 "data" 键下
            result.put("data", data);
        }
        return result;
    }

    // ==================== 1. 返回 ResponseEntity (Controller 常用) ====================

    /** 200 OK (无数据) */
    public static ResponseEntity<Map<String, Object>> ok() {
        return ok(null);
    }

    /** 200 OK (带数据) */
    public static ResponseEntity<Map<String, Object>> ok(Object data) {
        return ResponseEntity.ok(buildResponse(HttpStatus.OK, "OK", data));
    }

    /** 200 OK 的原始响应体 Map */
    public static Map<String, Object> okResponseMap(String msg, Map<String, Object> data) {
        return buildResponse(HttpStatus.OK, msg, data);
    }

    /**
     * 201 Created - 只包含消息，不包含数据体
     * @param msg 响应消息
     */
    public static ResponseEntity<Map<String, Object>> created(String msg) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(buildResponse(HttpStatus.CREATED, msg, null));
    }

    /**
     * 201 Created - 包含消息和数据体
     * 【新增的方法，用于支持 Controller 返回 BotVo】
     * @param msg 响应消息
     * @param data 响应数据（Map 形式）
     */
    public static ResponseEntity<Map<String, Object>> created(String msg, Map<String, Object> data) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(buildResponse(HttpStatus.CREATED, msg, data));
    }


    /** 400 Bad Request */
    public static ResponseEntity<Map<String, Object>> badRequest(String msg) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(buildResponse(HttpStatus.BAD_REQUEST, msg, null));
    }

    /** 401 Unauthorized */
    public static ResponseEntity<Map<String, Object>> unauthorized(String msg) {
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                .body(buildResponse(HttpStatus.UNAUTHORIZED, msg, null));
    }

    /** 403 Forbidden */
    public static ResponseEntity<Map<String, Object>> forbidden(String msg) {
        return ResponseEntity.status(HttpStatus.FORBIDDEN)
                .body(buildResponse(HttpStatus.FORBIDDEN, msg, null));
    }

    /** 404 Not Found */
    public static ResponseEntity<Map<String, Object>> notFound(String msg) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(buildResponse(HttpStatus.NOT_FOUND, msg, null));
    }

    /** 409 Conflict */
    public static ResponseEntity<Map<String, Object>> conflict(String msg) {
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(buildResponse(HttpStatus.CONFLICT, msg, null));
    }

    /** 409 Conflict with data */
    public static ResponseEntity<Map<String, Object>> conflict(String msg, Map<String, Object> data) {
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(buildResponse(HttpStatus.CONFLICT, msg, data));
    }

    /** 503 Service Unavailable */
    public static ResponseEntity<Map<String, Object>> serviceUnavailable(String msg) {
        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                .body(buildResponse(HttpStatus.SERVICE_UNAVAILABLE, msg, null));
    }

    /** 503 Service Unavailable with data */
    public static ResponseEntity<Map<String, Object>> serviceUnavailable(String msg, Map<String, Object> data) {
        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                .body(buildResponse(HttpStatus.SERVICE_UNAVAILABLE, msg, data));
    }

    /** 429 Too Many Requests (限流常用) */
    public static ResponseEntity<Map<String, Object>> tooManyRequests(String msg) {
        return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS)
                .body(buildResponse(HttpStatus.TOO_MANY_REQUESTS, msg, null));
    }

    /** 500 Internal Server Error */
    public static ResponseEntity<Map<String, Object>> internalError(String msg) {
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(buildResponse(HttpStatus.INTERNAL_SERVER_ERROR, msg, null));
    }

    // ==================== 2. 直接写入响应流 (Filter/Gateway 常用) ====================

    /**
     * 通用写入方法
     */
    public static Mono<Void> write(ServerWebExchange exchange, ResponseEntity<?> entity) {
        // 假设 WebExchangeUtils 存在
        return WebExchangeUtils.responseJson(exchange, entity);
    }

    /**
     * 快捷方法：写入错误 JSON (用于过滤器拦截，如鉴权失败)
     */
    public static Mono<Void> writeError(ServerWebExchange exchange, HttpStatus status, String msg) {
        ResponseEntity<Map<String, Object>> entity = ResponseEntity.status(status)
                .body(buildResponse(status, msg, null));
        return WebExchangeUtils.responseJson(exchange, entity);
    }

    /**
     * 快捷方法：默认写入 500 错误
     */
    public static Mono<Void> writeError(ServerWebExchange exchange, String msg) {
        return writeError(exchange, HttpStatus.INTERNAL_SERVER_ERROR, msg);
    }

    /** 500 Internal Error 的原始响应体 Map */
    public static Map<String, Object> errorResponseMap(String msg) {
        return buildResponse(HttpStatus.INTERNAL_SERVER_ERROR, msg, null);
    }

    /**
     * 快捷方法：写入成功 JSON (用于自定义响应)
     */
    public static Mono<Void> writeOk(ServerWebExchange exchange, Object data) {
        return WebExchangeUtils.responseJson(exchange, ok(data));
    }

    /** 201 Created 的原始响应体 Map */
    public static Map<String, Object> createdResponseMap(String msg, Map<String, Object> data) {
        return buildResponse(HttpStatus.CREATED, msg, data);
    }
}