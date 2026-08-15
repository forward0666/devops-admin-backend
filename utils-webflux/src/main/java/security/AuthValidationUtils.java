package security;

import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.util.StringUtils;
import org.springframework.web.server.ServerWebExchange;

@Slf4j
public class AuthValidationUtils {

    private AuthValidationUtils() {}

    public static void authorizedResponse(ServerHttpResponse response) {
        response.getHeaders().add("X-Authorization-Status", "OK");
    }

    @Deprecated
    public static boolean isAuthorized(ServerWebExchange exchange, String secret) {
        log.warn("isAuthorized is deprecated: use JWT or Signature auth via AuthFilter");
        return false;
    }
}