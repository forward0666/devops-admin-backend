package security;

import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.web.server.ServerWebExchange;

/**
 * 安全工具类 - 提供通用认证与响应封装逻辑
 *
 * 功能：
 * 1. 校验请求头 X-Encrypted-Data 是否匹配密钥
 * 2. 构造标准认证失败响应（401 / 405）
 * 3. 标记认证成功响应头
 * 4. 提供安全日志输出格式
 */
@Slf4j
public class AuthValidationUtils {

    private AuthValidationUtils() {
        // 工具类禁止实例化
    }

    /**
     * 验证请求头中的加密令牌是否与配置密钥匹配
     *
     * @param exchange ServerWebExchange 对象
     * @param secret   从配置中心获取的认证密钥
     * @return true 表示验证通过，false 表示不匹配
     */
    public static boolean isAuthorized(ServerWebExchange exchange, String secret) {
        String header = exchange.getRequest().getHeaders().getFirst("X-Encrypted-Data");
        return header != null && header.equals(secret);
    }

    /**
     * 返回未授权响应（401）
     */
    public static void unauthorizedResponse(ServerHttpResponse response, String logPrefix, String ip, String method, String path) {
        response.setStatusCode(HttpStatus.UNAUTHORIZED);
        response.getHeaders().add("X-Authorization-Status", "UNAUTHORIZED");
        log.warn("{} ❌ Unauthorized: IP={}, Method={}, Path={}", logPrefix, ip, method, path);
    }

    /**
     * 返回禁止访问响应（405）
     */
    public static void methodNotAllowedResponse(ServerHttpResponse response, String logPrefix, String ip, String method, String path) {
        response.setStatusCode(HttpStatus.METHOD_NOT_ALLOWED);
        response.getHeaders().add("X-Authorization-Status", "NOT_ALLOWED");
        log.warn("{} ⛔ Method not allowed: IP={}, Method={}, Path={}", logPrefix, ip, method, path);
    }

    /**
     * 在响应头中标记认证成功
     */
    public static void authorizedResponse(ServerHttpResponse response) {
        response.getHeaders().add("X-Authorization-Status", "OK");
    }
}
