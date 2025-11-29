package exception;

import lombok.extern.slf4j.Slf4j;
import network.HttpResponseUtils;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.server.ServerWebInputException; // 导入新增的异常类
import reactor.core.publisher.Mono;
import reactor.netty.channel.AbortedException;

import java.util.Map;
import java.util.concurrent.TimeoutException;

/**
 * 全局异常处理器，使用 @ControllerAdvice 捕获所有控制器中抛出的异常，
 * 并返回标准化的 JSON 错误响应。
 */
@ControllerAdvice
@Slf4j
public class GlobalException { // <-- 使用您指定的类名

    /**
     * 捕获 Spring WebFlux 在解析请求体或路径参数时抛出的异常，如
     * 400 BAD_REQUEST "No request body" 或类型不匹配。
     * 返回 400 Bad Request 响应。
     */
    @ExceptionHandler(ServerWebInputException.class)
    public Mono<ResponseEntity<Map<String, Object>>> handleServerWebInputException(ServerWebInputException ex) {
        log.warn("⚠️ Global ServerWebInputException caught (400 Bad Request): {}", ex.getMessage());
        // ServerWebInputException 通常会携带一个明确的 reason，我们使用它来构建 400 响应
        String reason = ex.getReason() != null ? ex.getReason() : ex.getMessage();
        // 假设 HttpResponseUtils.badRequest(message) 返回 HttpStatus.BAD_REQUEST (400) 的 ResponseEntity
        return Mono.just(HttpResponseUtils.badRequest(reason));
    }

    /**
     * 捕获自定义的 InvalidRequestException，用于处理客户端请求参数或格式错误。
     * 返回 400 Bad Request 响应。
     */
    @ExceptionHandler(InvalidRequestException.class)
    public Mono<ResponseEntity<Map<String, Object>>> handleInvalidRequestException(InvalidRequestException ex) {
        log.warn("⚠️ Global InvalidRequestException caught (400 Bad Request): {}", ex.getMessage());
        // 假设 HttpResponseUtils.badRequest(message) 返回 HttpStatus.BAD_REQUEST (400) 的 ResponseEntity
        return Mono.just(HttpResponseUtils.badRequest(ex.getMessage()));
    }

    /**
     * 捕获 Spring WebFlux 在尝试写入响应时遇到的 UnsupportedOperationException，
     * 通常是因为在响应已提交后尝试修改只读的 HTTP Headers（如 Content-Type）。
     * 这不是一个需要用户干预的内部错误，通常是请求生命周期结束的副作用。
     */
    @ExceptionHandler(UnsupportedOperationException.class)
    public Mono<ResponseEntity<Map<String, Object>>> handleUnsupportedOperationException(UnsupportedOperationException ex) {
        // 专门针对 ReadOnlyHttpHeaders 导致的异常进行降级处理
        if (ex.getMessage() == null || ex.getMessage().contains("ReadOnlyHttpHeaders")) {
            log.warn("⚠️ Attempted modification of ReadOnlyHttpHeaders during error response writing. Dropping response gracefully.");
            return Mono.just(ResponseEntity.status(HttpStatus.NO_CONTENT).build());
        }
        // 如果不是 ReadOnlyHttpHeaders 导致的，则按常规运行时错误处理
        log.error("🚨 Global UnsupportedOperationException caught: ", ex);
        return Mono.just(HttpResponseUtils.internalError("服务器内部操作不支持: " + ex.getMessage()));
    }

    /**
     * 捕获 AbortedException，当客户端在服务器处理完成前断开连接时发生。
     * 这不是一个服务器错误，应以 WARN 级别记录并返回一个空响应。
     */
    @ExceptionHandler(AbortedException.class)
    public Mono<ResponseEntity<Map<String, Object>>> handleAbortedException(AbortedException ex) {
        // 降低日志级别，因为它通常是客户端行为 (如关闭浏览器) 导致的，而不是服务器错误。
        log.warn("⚠️ Client aborted connection (AbortedException): {}", ex.getMessage());

        // 无法向已关闭的连接发送响应，但返回 NO_CONTENT 确保 WebFlux 链完成。
        return Mono.just(ResponseEntity.status(HttpStatus.NO_CONTENT).build());
    }

    /**
     * 捕获所有 TimeoutException 及其子类 (包括 AggressiveTimeoutException)，
     * 通常由 WebClient 或 Reactor 运算符抛出。
     * 返回 504 Gateway Timeout 响应。
     * 此处理器仅对非 Webhook 路径生效，Webhook 路径由 Filter 处理。
     */
    @ExceptionHandler(TimeoutException.class)
    public Mono<ResponseEntity<Map<String, Object>>> handleTimeoutException(TimeoutException ex) {
        log.error("🚨 Global TimeoutException caught: {}", ex.getMessage());
        String message = "请求处理超时，请稍后重试。";
        // 假设 AggressiveTimeoutException 在同一包下
        if (ex instanceof AggressiveTimeoutException) {
            message = "系统强制执行的积极超时。";
        }

        // 返回 504 Gateway Timeout
        // 使用 HttpResponseUtils 的 internalError 方法生成主体结构
        Map<String, Object> errorBody = HttpResponseUtils.internalError(message).getBody();

        // 确保响应体中的 'code' 字段反映正确的 504 状态码
        if (errorBody != null) {
            errorBody.put("code", HttpStatus.GATEWAY_TIMEOUT.value());
            errorBody.put("status", "error");
        }

        return Mono.just(ResponseEntity
                .status(HttpStatus.GATEWAY_TIMEOUT)
                .body(errorBody));
    }

    /**
     * 捕获所有未被处理的 RuntimeException。
     * 返回 500 Internal Server Error 响应。
     */
    @ExceptionHandler(RuntimeException.class)
    public Mono<ResponseEntity<Map<String, Object>>> handleRuntimeException(RuntimeException ex) {
        log.error("🚨 Global RuntimeException caught: ", ex);
        return Mono.just(HttpResponseUtils.internalError("服务器内部错误: " + ex.getMessage()));
    }

    /**
     * 捕获所有其他的 Exception。
     * 返回 500 Internal Server Error 响应。
     */
    @ExceptionHandler(Exception.class)
    public Mono<ResponseEntity<Map<String, Object>>> handleException(Exception ex) {
        log.error("🚨 Global Exception caught: ", ex);
        return Mono.just(HttpResponseUtils.internalError("发生未知错误: " + ex.getMessage()));
    }
}