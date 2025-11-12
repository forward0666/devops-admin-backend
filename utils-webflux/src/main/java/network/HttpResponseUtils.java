package network;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.server.reactive.ServerHttpResponse;
import reactor.core.publisher.Mono;
import webflux.ResponseWriterUtils;

import java.util.HashMap;
import java.util.Map;

/**
 * HTTP 响应工具类
 * 提供统一的 JSON 响应封装，支持返回 status、code、message、data
 */
public class HttpResponseUtils {

    private static Map<String, Object> buildResponse(HttpStatus status, String msg, Map<String, Object> data) {
        Map<String, Object> result = new HashMap<>();
        result.put("status", status.is2xxSuccessful() ? "ok" : "error");
        result.put("code", status.value());
        result.put("message", msg != null ? msg : status.getReasonPhrase());
        if (data != null && !data.isEmpty()) {
            result.put("data", data);
        }
        return result;
    }

    /** 200 OK */
    public static ResponseEntity<Map<String, Object>> ok() {
        return ok(null);
    }

    public static ResponseEntity<Map<String, Object>> ok(Map<String, Object> data) {
        return ResponseEntity.ok(buildResponse(HttpStatus.OK, "OK", data));
    }

    /** 201 Created */
    public static ResponseEntity<Map<String, Object>> created(String msg) {
        return ResponseEntity.status(HttpStatus.CREATED).body(buildResponse(HttpStatus.CREATED, msg, null));
    }

    /** 204 No Content */
    public static ResponseEntity<Map<String, Object>> noContent() {
        return ResponseEntity.status(HttpStatus.NO_CONTENT).body(buildResponse(HttpStatus.NO_CONTENT, "No Content", null));
    }

    /** 400 Bad Request */
    public static ResponseEntity<Map<String, Object>> badRequest(String msg) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(buildResponse(HttpStatus.BAD_REQUEST, msg, null));
    }

    /** 401 Unauthorized */
    public static ResponseEntity<Map<String, Object>> unauthorized(String msg) {
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(buildResponse(HttpStatus.UNAUTHORIZED, msg, null));
    }

    /** 403 Forbidden */
    public static ResponseEntity<Map<String, Object>> forbidden(String msg) {
        return ResponseEntity.status(HttpStatus.FORBIDDEN).body(buildResponse(HttpStatus.FORBIDDEN, msg, null));
    }

    /** 404 Not Found */
    public static ResponseEntity<Map<String, Object>> notFound(String msg) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(buildResponse(HttpStatus.NOT_FOUND, msg, null));
    }

    /** 405 Method Not Allowed */
    public static ResponseEntity<Map<String, Object>> methodNotAllowed(String msg) {
        return ResponseEntity.status(HttpStatus.METHOD_NOT_ALLOWED).body(buildResponse(HttpStatus.METHOD_NOT_ALLOWED, msg, null));
    }

    /** 409 Conflict */
    public static ResponseEntity<Map<String, Object>> conflict(String msg) {
        return ResponseEntity.status(HttpStatus.CONFLICT).body(buildResponse(HttpStatus.CONFLICT, msg, null));
    }

    /** 422 Unprocessable Entity */
    public static ResponseEntity<Map<String, Object>> unprocessableEntity(String msg) {
        return ResponseEntity.status(HttpStatus.UNPROCESSABLE_ENTITY).body(buildResponse(HttpStatus.UNPROCESSABLE_ENTITY, msg, null));
    }

    /** 500 Internal Server Error */
    public static ResponseEntity<Map<String, Object>> internalError(String msg) {
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(buildResponse(HttpStatus.INTERNAL_SERVER_ERROR, msg, null));
    }

    /** 503 Service Unavailable */
    public static ResponseEntity<Map<String, Object>> serviceUnavailable(String msg) {
        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).body(buildResponse(HttpStatus.SERVICE_UNAVAILABLE, msg, null));
    }
    /** 直接将 ResponseEntity 写入响应流 */
    public static Mono<Void> write(ServerHttpResponse response, ResponseEntity<?> entity) {
        return ResponseWriterUtils.writeJson(response, entity);
    }

    /** 快捷方法：直接输出错误 JSON */
    public static Mono<Void> writeError(ServerHttpResponse response, String msg) {
        return ResponseWriterUtils.writeJson(response, internalError(msg));
    }

    /** 快捷方法：直接输出成功 JSON */
    public static Mono<Void> writeOk(ServerHttpResponse response, Map<String, Object> data) {
        return ResponseWriterUtils.writeJson(response, ok(data));
    }

}
