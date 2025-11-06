package network;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.util.HashMap;
import java.util.Map;

/**
 * HTTP 响应工具类
 * 提供统一的 JSON 响应封装
 */
public class HttpResponseUtils {

    /** 返回 400 Bad Request */
    public static ResponseEntity<Map<String, Object>> badRequest(String msg) {
        Map<String, Object> error = new HashMap<>();
        error.put("status", "error");
        error.put("message", msg);
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(error);
    }

    /** 返回 500 Internal Server Error */
    public static ResponseEntity<Map<String, Object>> internalError(String msg) {
        Map<String, Object> error = new HashMap<>();
        error.put("status", "error");
        error.put("message", msg);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(error);
    }

    /** 返回 200 OK */
    public static ResponseEntity<Map<String, Object>> ok(Map<String, Object> data) {
        Map<String, Object> result = new HashMap<>(data);
        result.putIfAbsent("status", "ok");
        return ResponseEntity.ok(result);
    }
}
