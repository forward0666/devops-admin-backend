package security;

import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.util.StringUtils;
import org.springframework.web.server.ServerWebExchange;

/**
 * 安全工具类 - 提供通用认证逻辑
 */
@Slf4j
public class AuthValidationUtils {

    private AuthValidationUtils() {
        // 工具类禁止实例化
    }

    // --- 定义常量 Header Key ---
    private static final String CUSTOM_AUTH_HEADER = "X-Encrypted-Data";
    private static final String TELEGRAM_SECRET_HEADER = "X-Telegram-Bot-Api-Secret-Token";

    /**
     * 验证请求头中的加密令牌是否与配置密钥匹配
     * 逻辑：首先检查自定义 Header，如果不存在，则检查 Telegram Secret Header。
     *
     * @param exchange ServerWebExchange 对象
     * @param secret   从配置中心获取的认证密钥
     * @return true 表示验证通过，false 表示不匹配
     */
    public static boolean isAuthorized(ServerWebExchange exchange, String secret) {
        if (!StringUtils.hasText(secret)) {
            // 如果密钥未配置，视为认证失败或默认放行（取决于业务安全要求）
            log.error("Authentication secret is empty. Denying request.");
            return false;
        }

        // 1. 检查自定义 Header (X-Encrypted-Data)
        String customHeaderValue = exchange.getRequest().getHeaders().getFirst(CUSTOM_AUTH_HEADER);
        if (customHeaderValue != null && customHeaderValue.equals(secret)) {
            log.debug("Authorized via custom header: {}", CUSTOM_AUTH_HEADER);
            return true;
        }

        // 2. 检查 Telegram Secret Header (X-Telegram-Bot-Api-Secret-Token)
        String telegramSecretValue = exchange.getRequest().getHeaders().getFirst(TELEGRAM_SECRET_HEADER);
        if (telegramSecretValue != null && telegramSecretValue.equals(secret)) {
            log.debug("Authorized via Telegram header: {}", TELEGRAM_SECRET_HEADER);
            return true;
        }

        // 3. 两个 Header 都不匹配
        return false;
    }

    /**
     * ⚠️ 移除: unauthorizedResponse 和 methodNotAllowedResponse
     * 理由：在 AuthFilter 中统一使用 HttpResponseUtils 终止流，避免 WebFlux 响应逻辑不一致。
     */

    /**
     * 在响应头中标记认证成功
     */
    public static void authorizedResponse(ServerHttpResponse response) {
        response.getHeaders().add("X-Authorization-Status", "OK");
    }
}