package com.backend.bot.util;

import com.backend.bot.constants.TelegramConstants;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.BodyInserters;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;
import reactor.util.retry.Retry;

import java.util.HashMap;
import java.util.Map;
/**
 * Telegram API 请求构建器
 * 
 * 该类提供流式 API 用于构建 Telegram API 请求，遵循 DRY 原则，
 * 减少重复的 API 调用代码。统一处理请求构建、错误处理和响应处理。
 * 
 * 主要功能：
 * 1. 流式 API 构建 Telegram 请求
 * 2. 统一处理请求参数和验证
 * 3. 标准化错误处理和重试机制
 * 4. 统一响应处理和日志记录
 * 
 * 使用示例：
 * <pre>
 * TelegramApiRequestBuilder.create()
 *     .path("/sendMessage")
 *     .chatId(chatId)
 *     .text("Hello")
 *     .replyMarkup(keyboard)
 *     .execute(webClient, retryPolicy);
 * </pre>
 * 
 * @author Backend Team
 * @version 1.0.0
 */
@Slf4j
@Component
public class TelegramApiRequestBuilder {

    /**
     * 请求构建器类
     * 
     * 使用流式 API 构建 Telegram API 请求，支持链式调用。
     * 内部维护请求参数和配置，最终生成可执行的 Mono。
     * 
     * 支持的参数：
     * 1. chatId - 聊天 ID
     * 2. text - 消息文本
     * 3. messageId - 消息 ID（用于编辑/删除）
     * 4. replyMarkup - 回复标记（内联键盘等）
     * 5. parseMode - 解析模式（HTML/Markdown）
     * 6. 额外参数 - 通过 customParam 添加
     */
    public static class RequestBuilder {
        
        // 请求参数映射
        private final Map<String, Object> bodyMap = new HashMap<>();
        
        // 请求路径
        private String path;
        
        // 是否期望响应（如获取消息 ID）
        private boolean expectsResponse = false;
        
        // 成功日志模板
        private String successLogTemplate;
        
        // 错误日志模板
        private String errorLogTemplate;
        
        // 重试策略
        private Retry retryPolicy;
        
        // 请求方法类型
        private RequestMethod requestMethod = RequestMethod.POST;

        /**
         * 设置请求路径
         * 
         * @param path API 端点路径，如 "/sendMessage"
         * @return 当前构建器实例
         */
        public RequestBuilder path(String path) {
            this.path = path;
            return this;
        }
        
        /**
         * 设置聊天 ID
         * 
         * @param chatId 聊天 ID
         * @return 当前构建器实例
         */
        public RequestBuilder chatId(Long chatId) {
            bodyMap.put("chat_id", chatId);
            return this;
        }
        
        /**
         * 设置消息文本
         * 
         * @param text 消息文本内容
         * @return 当前构建器实例
         */
        public RequestBuilder text(String text) {
            bodyMap.put("text", text);
            return this;
        }
        
        /**
         * 设置消息 ID
         * 
         * 用于编辑或删除消息的标识。
         * 
         * @param messageId 消息 ID
         * @return 当前构建器实例
         */
        public RequestBuilder messageId(Long messageId) {
            bodyMap.put("message_id", messageId);
            return this;
        }
        
        /**
         * 设置解析模式
         * 
         * 支持的格式：
         * - "HTML" - HTML 格式
         * - "Markdown" - Markdown 格式
         * - "MarkdownV2" - Markdown V2 格式
         * 
         * @param parseMode 解析模式
         * @return 当前构建器实例
         */
        public RequestBuilder parseMode(String parseMode) {
            if (parseMode != null && !parseMode.isEmpty()) {
                bodyMap.put("parse_mode", parseMode);
            }
            return this;
        }
        
        /**
         * 设置回复标记
         * 
         * 用于添加内联键盘、回复键盘等交互元素。
         * 
         * @param replyMarkup 回复标记对象（通常为内联键盘）
         * @return 当前构建器实例
         */
        public RequestBuilder replyMarkup(Object replyMarkup) {
            if (replyMarkup != null) {
                bodyMap.put("reply_markup", replyMarkup);
            }
            return this;
        }
        
        /**
         * 设置自定义参数
         * 
         * 用于添加 Telegram API 支持的其他参数。
         * 
         * @param key 参数名
         * @param value 参数值
         * @return 当前构建器实例
         */
        public RequestBuilder customParam(String key, Object value) {
            if (key != null && !key.isEmpty() && value != null) {
                bodyMap.put(key, value);
            }
            return this;
        }
        
        /**
         * 设置期望响应
         * 
         * 标记当前请求期望获取响应内容（如消息 ID）。
         * 默认情况下，请求不返回具体内容。
         * 
         * @return 当前构建器实例
         */
        public RequestBuilder expectsResponse() {
            this.expectsResponse = true;
            return this;
        }
        
        /**
         * 设置日志模板
         * 
         * 用于自定义成功和错误日志的格式。
         * 
         * @param success 成功日志模板
         * @param error 错误日志模板
         * @return 当前构建器实例
         */
        public RequestBuilder logTemplates(String success, String error) {
            this.successLogTemplate = success;
            this.errorLogTemplate = error;
            return this;
        }
        
        /**
         * 设置重试策略
         * 
         * @param retryPolicy 重试策略配置
         * @return 当前构建器实例
         */
        public RequestBuilder retryPolicy(Retry retryPolicy) {
            this.retryPolicy = retryPolicy;
            return this;
        }
        
        /**
         * 设置请求方法
         * 
         * @param requestMethod 请求方法类型
         * @return 当前构建器实例
         */
        public RequestBuilder method(RequestMethod requestMethod) {
            this.requestMethod = requestMethod;
            return this;
        }
        
        /**
         * 执行请求并返回响应
         * 
         * 使用提供的 WebClient 执行构建的请求，期望有响应内容。
         * 自动处理上下文传播、错误处理和日志记录。
         * 
         * @param webClient 用于发送请求的 WebClient 实例
         * @param defaultRetryPolicy 默认重试策略（如果未指定）
         * @return 响应 Mono<String>，包含 API 响应内容
         */
        public Mono<String> executeWithResponse(WebClient webClient, Retry defaultRetryPolicy) {
            // 使用实际指定的重试策略或默认策略
            Retry actualRetryPolicy = retryPolicy != null ? retryPolicy : defaultRetryPolicy;
            
            return ReactiveOperationTemplate.<String>withTraceContextAndErrorHandling(
                op -> {
                    // 验证必要参数
                    if (path == null || path.isEmpty()) {
                        throw new IllegalArgumentException("API path cannot be null or empty");
                    }
                    
                    // 构建请求
                    WebClient.RequestHeadersSpec<?> requestSpec = buildRequest(webClient);
                    
                    // 处理响应
                    return requestSpec.retrieve()
                        .bodyToMono(String.class)
                        .retryWhen(actualRetryPolicy)
                        .doOnSuccess(res -> logSuccess(op.traceLogPrefix(), res));
                },
                // 统一错误处理
                (Throwable e) -> Mono.<String>error(e)
            );
        }
        
        /**
         * 执行请求，不关心响应内容
         * 
         * 使用提供的 WebClient 执行构建的请求，不关心响应内容。
         * 自动处理上下文传播、错误处理和日志记录。
         * 
         * @param webClient 用于发送请求的 WebClient 实例
         * @param defaultRetryPolicy 默认重试策略（如果未指定）
         * @return 响应 Mono<Void>，表示操作完成
         */
        public Mono<Void> executeWithoutResponse(WebClient webClient, Retry defaultRetryPolicy) {
            // 使用实际指定的重试策略或默认策略
            Retry actualRetryPolicy = retryPolicy != null ? retryPolicy : defaultRetryPolicy;
            
            return ReactiveOperationTemplate.<Void>withTraceContextAndErrorHandling(
                op -> {
                    // 验证必要参数
                    if (path == null || path.isEmpty()) {
                        throw new IllegalArgumentException("API path cannot be null or empty");
                    }
                    
                    // 构建请求
                    WebClient.RequestHeadersSpec<?> requestSpec = buildRequest(webClient);
                    
                    // 处理响应
                    return requestSpec.retrieve()
                        .toBodilessEntity()
                        .retryWhen(actualRetryPolicy)
                        .doOnSuccess(res -> logSuccess(op.traceLogPrefix(), res))
                        .then();
                },
                // 统一错误处理
                (Throwable e) -> Mono.<Void>error(e)
            );
        }
        
        /**
         * 兼容性方法，根据 expectsResponse 设置返回合适的类型
         * 
         * @deprecated 建议使用 executeWithResponse 或 executeWithoutResponse
         */
        @Deprecated
        public Mono<Object> execute(WebClient webClient, Retry defaultRetryPolicy) {
            if (expectsResponse) {
                return executeWithResponse(webClient, defaultRetryPolicy).cast(Object.class);
            } else {
                return executeWithoutResponse(webClient, defaultRetryPolicy).cast(Object.class);
            }
        }
        
        /**
         * 构建请求对象
         * 
         * 根据请求方法和参数构建 WebClient 请求。
         * 
         * @param webClient WebClient 实例
         * @return 构建好的请求规范
         */
        private WebClient.RequestHeadersSpec<?> buildRequest(WebClient webClient) {
            WebClient.RequestHeadersSpec<?> request;
            switch (requestMethod) {
                case POST:
                    request = webClient.post().uri(path);
                    break;
                case GET:
                    request = webClient.get().uri(path, uriBuilder -> {
                        bodyMap.forEach(uriBuilder::queryParam);
                        return uriBuilder.build();
                    });
                    break;
                case DELETE:
                    request = webClient.delete().uri(path);
                    break;
                default:
                    request = webClient.post().uri(path);
                    break;
            };
            
            // 对于 POST 和 DELETE 请求，添加请求体
            if (requestMethod != RequestMethod.GET && !bodyMap.isEmpty() && request instanceof WebClient.RequestBodySpec) {
                ((WebClient.RequestBodySpec) request).body(BodyInserters.fromValue(bodyMap));
            }
            
            return request;
        }
        
        /**
         * 记录成功日志
         * 
         * @param traceLogPrefix 追踪日志前缀
         * @param response 响应对象
         */
        private void logSuccess(String traceLogPrefix, Object response) {
            if (successLogTemplate != null) {
                log.info("{}" + successLogTemplate, traceLogPrefix);
            } else {
                log.debug("{}Telegram API call successful: {}", traceLogPrefix, path);
            }
        }
    }
    
    /**
     * 请求方法枚举
     * 
     * 定义支持的 HTTP 请求方法类型。
     */
    public enum RequestMethod {
        GET, POST, DELETE
    }
    
    /**
     * 创建新的请求构建器
     * 
     * @return 新的 RequestBuilder 实例
     */
    public static RequestBuilder create() {
        return new RequestBuilder();
    }
    
    /**
     * 创建设置 Webhook 请求的便捷方法
     * 
     * @param token Bot Token
     * @param webhookUrl Webhook URL
     * @param secretToken 安全令牌
     * @return 预配置的 RequestBuilder 实例
     */
    public static RequestBuilder setWebhook(String token, String webhookUrl, String secretToken) {
        String path = String.format(TelegramConstants.API_PATH_TEMPLATE, 
                                token, TelegramConstants.API_METHOD_SET_WEBHOOK);
        
        return create()
            .path(path)
            .customParam("url", webhookUrl)
            .customParam("secret_token", secretToken)
            .expectsResponse()
            .logTemplates("Webhook configuration updated successfully", 
                         "Failed to configure webhook");
    }
    
    /**
     * 创建发送消息请求的便捷方法
     * 
     * @param token Bot Token
     * @return 预配置的 RequestBuilder 实例
     */
    public static RequestBuilder sendMessage(String token) {
        String path = String.format(TelegramConstants.API_PATH_TEMPLATE, 
                                token, TelegramConstants.API_METHOD_SEND_MESSAGE);
        
        return create()
            .path(path)
            .method(RequestMethod.POST)
            .logTemplates("Message sent successfully", 
                         "Failed to send message");
    }
    
    /**
     * 创建删除消息请求的便捷方法
     * 
     * @param token Bot Token
     * @return 预配置的 RequestBuilder 实例
     */
    public static RequestBuilder deleteMessage(String token) {
        String path = String.format(TelegramConstants.API_PATH_TEMPLATE, 
                                token, TelegramConstants.API_METHOD_DELETE_MESSAGE);
        
        return create()
            .path(path)
            .method(RequestMethod.POST)
            .logTemplates("Message deleted successfully", 
                         "Failed to delete message");
    }
    
    /**
     * 创建编辑消息请求的便捷方法
     * 
     * @param token Bot Token
     * @return 预配置的 RequestBuilder 实例
     */
    public static RequestBuilder editMessageText(String token) {
        String path = String.format(TelegramConstants.API_PATH_TEMPLATE, 
                                token, TelegramConstants.API_METHOD_EDIT_MESSAGE_TEXT);
        
        return create()
            .path(path)
            .method(RequestMethod.POST)
            .expectsResponse()
            .logTemplates("Message edited successfully", 
                         "Failed to edit message");
    }
}