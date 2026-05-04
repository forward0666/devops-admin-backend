package com.backend.bot.util;

import lombok.extern.slf4j.Slf4j;
import reactor.core.publisher.Mono;

import java.util.function.Function;

/**
 * 错误处理工具类
 * 
 * 该类提供标准化的响应式错误处理函数，遵循 DRY 原则，
 * 减少重复的错误处理代码。所有错误处理函数都使用
 * ReactiveOperationTemplate 确保上下文正确传播。
 * 
 * 主要功能：
 * 1. 统一错误日志格式
 * 2. 标准化错误处理策略
 * 3. 简化错误处理代码
 * 4. 保持上下文传播
 * 
 * 错误处理策略：
 * 1. logAndReturnEmpty - 记录错误并返回空结果
 * 2. logAndReturnError - 记录错误并传播错误
 * 3. logAndWarn - 记录警告并返回空结果
 * 4. logAndReturnDefault - 记录错误并返回默认值
 * 
 * @author Backend Team
 * @version 1.0.0
 */
@Slf4j
public final class ErrorHandlerUtils {

    /**
     * 私有构造函数，防止工具类实例化
     */
    private ErrorHandlerUtils() {}

    /**
     * 记录错误并返回空结果
     * 
     * 最常用的错误处理策略，适用于非关键操作失败的情况。
     * 不会中断响应式链，而是返回空结果继续执行。
     * 
     * 日志格式：ERROR [traceId=xxx] Failed to {operation}: {error_message}
     * 
     * @param operation 操作描述，如 "send message", "update session"
     * @param <T> 返回类型
     * @return 错误处理函数，记录错误并返回 Mono.empty()
     */
    public static <T> Function<Exception, Mono<T>> logAndReturnEmpty(String operation) {
        return e -> ReactiveOperationTemplate.withTraceContext(op -> {
            log.error("{}❌ Failed to {}: {}", op.traceLogPrefix(), operation, e.getMessage());
            return Mono.empty();
        });
    }

    /**
     * 记录错误并返回空结果（带详细日志）
     * 
     * 与 logAndReturnEmpty 类似，但会记录完整的堆栈跟踪信息。
     * 适用于需要详细调试信息的错误情况。
     * 
     * 日志格式：ERROR [traceId=xxx] Failed to {operation}
     * 
     * @param operation 操作描述，如 "send message", "update session"
     * @param <T> 返回类型
     * @return 错误处理函数，记录详细错误并返回 Mono.empty()
     */
    public static <T> Function<Exception, Mono<T>> logAndReturnEmptyWithStackTrace(String operation) {
        return e -> ReactiveOperationTemplate.withTraceContext(op -> {
            log.error("{}❌ Failed to {}", op.traceLogPrefix(), operation, e);
            return Mono.empty();
        });
    }

    /**
     * 记录错误并传播错误
     * 
     * 用于关键操作失败的情况，需要中断响应式链并向上传播错误。
     * 适用于不能静默失败的错误情况。
     * 
     * 日志格式：ERROR [traceId=xxx] Failed to {operation}: {error_message}
     * 
     * @param operation 操作描述，如 "send message", "update session"
     * @param <T> 返回类型
     * @return 错误处理函数，记录错误并传播原始异常
     */
    public static <T> Function<Exception, Mono<T>> logAndReturnError(String operation) {
        return e -> ReactiveOperationTemplate.withTraceContext(op -> {
            log.error("{}❌ Failed to {}: {}", op.traceLogPrefix(), operation, e.getMessage());
            return Mono.error(e);
        });
    }

    /**
     * 记录错误并传播错误（带详细日志）
     * 
     * 与 logAndReturnError 类似，但会记录完整的堆栈跟踪信息。
     * 适用于需要详细调试信息的严重错误情况。
     * 
     * 日志格式：ERROR [traceId=xxx] Failed to {operation}
     * 
     * @param operation 操作描述，如 "send message", "update session"
     * @param <T> 返回类型
     * @return 错误处理函数，记录详细错误并传播原始异常
     */
    public static <T> Function<Exception, Mono<T>> logAndReturnErrorWithStackTrace(String operation) {
        return e -> ReactiveOperationTemplate.withTraceContext(op -> {
            log.error("{}❌ Failed to {}", op.traceLogPrefix(), operation, e);
            return Mono.error(e);
        });
    }

    /**
     * 记录警告并返回空结果
     * 
     * 用于非致命性错误或预期中的错误情况。
     * 不会中断响应式链，而是返回空结果继续执行。
     * 
     * 日志格式：WARN [traceId=xxx] Warning during {operation}: {error_message}
     * 
     * @param operation 操作描述，如 "send message", "update session"
     * @param <T> 返回类型
     * @return 错误处理函数，记录警告并返回 Mono.empty()
     */
    public static <T> Function<Exception, Mono<T>> logAndWarn(String operation) {
        return e -> ReactiveOperationTemplate.withTraceContext(op -> {
            log.warn("{}⚠️ Warning during {}: {}", op.traceLogPrefix(), operation, e.getMessage());
            return Mono.empty();
        });
    }

    /**
     * 记录警告并返回空结果（带详细日志）
     * 
     * 与 logAndWarn 类似，但会记录完整的堆栈跟踪信息。
     * 适用于需要详细调试信息的警告情况。
     * 
     * 日志格式：WARN [traceId=xxx] Warning during {operation}
     * 
     * @param operation 操作描述，如 "send message", "update session"
     * @param <T> 返回类型
     * @return 错误处理函数，记录详细警告并返回 Mono.empty()
     */
    public static <T> Function<Exception, Mono<T>> logAndWarnWithStackTrace(String operation) {
        return e -> ReactiveOperationTemplate.withTraceContext(op -> {
            log.warn("{}⚠️ Warning during {}", op.traceLogPrefix(), operation, e);
            return Mono.empty();
        });
    }

    /**
     * 记录错误并返回默认值
     * 
     * 用于需要返回默认值而非空结果的情况。
     * 适用于业务逻辑中需要备选值的场景。
     * 
     * 日志格式：ERROR [traceId=xxx] Failed to {operation}, returning default: {error_message}
     * 
     * @param operation 操作描述，如 "send message", "update session"
     * @param defaultValue 要返回的默认值
     * @param <T> 返回类型
     * @return 错误处理函数，记录错误并返回默认值
     */
    public static <T> Function<Exception, Mono<T>> logAndReturnDefault(String operation, T defaultValue) {
        return e -> ReactiveOperationTemplate.withTraceContext(op -> {
            log.error("{}❌ Failed to {}, returning default: {}", op.traceLogPrefix(), operation, e.getMessage());
            return Mono.just(defaultValue);
        });
    }

    /**
     * 记录错误并返回默认值（带详细日志）
     * 
     * 与 logAndReturnDefault 类似，但会记录完整的堆栈跟踪信息。
     * 适用于需要详细调试信息的默认值返回情况。
     * 
     * 日志格式：ERROR [traceId=xxx] Failed to {operation}, returning default
     * 
     * @param operation 操作描述，如 "send message", "update session"
     * @param defaultValue 要返回的默认值
     * @param <T> 返回类型
     * @return 错误处理函数，记录详细错误并返回默认值
     */
    public static <T> Function<Exception, Mono<T>> logAndReturnDefaultWithStackTrace(String operation, T defaultValue) {
        return e -> ReactiveOperationTemplate.withTraceContext(op -> {
            log.error("{}❌ Failed to {}, returning default", op.traceLogPrefix(), operation, e);
            return Mono.just(defaultValue);
        });
    }
}