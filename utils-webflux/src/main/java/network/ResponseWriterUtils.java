package network;

import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.core.io.buffer.DataBuffer;
import reactor.core.publisher.Mono;

import java.nio.charset.StandardCharsets;

/**
 * WebFlux 响应输出工具类
 */
public class ResponseWriterUtils {

    /**
     * 写入 JSON 响应
     */
    public static Mono<Void> writeJson(ServerHttpResponse response, ResponseEntity<?> entity) {
        response.setStatusCode(entity.getStatusCode());
        response.getHeaders().setContentType(MediaType.APPLICATION_JSON);

        Object body = entity.getBody();
        String json = (body instanceof String) ? (String) body : String.valueOf(body);

        DataBuffer buffer = response.bufferFactory()
                .wrap(json.getBytes(StandardCharsets.UTF_8));
        return response.writeWith(Mono.just(buffer));
    }

    /**
     * 写入 JSON 字符串（无 ResponseEntity）
     */
    public static Mono<Void> writeJson(ServerHttpResponse response, String json, int statusCode) {
        response.setStatusCode(HttpStatus.valueOf(statusCode));
        response.getHeaders().setContentType(MediaType.APPLICATION_JSON);

        DataBuffer buffer = response.bufferFactory()
                .wrap(json.getBytes(StandardCharsets.UTF_8));
        return response.writeWith(Mono.just(buffer));
    }

    /**
     * 写入纯文本响应
     */
    public static Mono<Void> writeText(ServerHttpResponse response, String text, int statusCode) {
        response.setStatusCode(HttpStatus.valueOf(statusCode));
        response.getHeaders().setContentType(MediaType.TEXT_PLAIN);

        DataBuffer buffer = response.bufferFactory()
                .wrap(text.getBytes(StandardCharsets.UTF_8));
        return response.writeWith(Mono.just(buffer));
    }
}
