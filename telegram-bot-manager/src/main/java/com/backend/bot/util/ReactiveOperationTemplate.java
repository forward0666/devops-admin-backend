package com.backend.bot.util;

import lombok.extern.slf4j.Slf4j;
import reactor.core.publisher.Mono;
import reactor.util.context.ContextView;

import java.util.function.Function;

/**
 * 响应式操作模板类
 * 
 * 该类提供响应式编程的通用模板方法，用于简化重复的上下文处理、
 * 日志记录和错误处理逻辑。遵循 DRY 原则，减少代码重复。
 * 
 * 主要功能：
 * 1. 统一处理 Reactor ContextView 和 MDC 同步
 * 2. 提供标准化的日志前缀
 * 3. 简化错误处理逻辑
 * 4. 确保上下文传播的正确性
 * 
 * 使用场景：
 * - 所有需要处理响应式上下文的 Service 和 Handler
 * - 需要统一日志记录的响应式操作
 * - 需要错误处理的响应式链
 * - 需要追踪 ID 传播的操作
 * 
 * 设计模式：
 * 1. 模板方法模式 - 提供标准化的操作模板
 * 2. 函数式编程 - 使用 Function 传递业务逻辑
 * 3. 上下文传播 - 确保 Reactor Context 正确传递
 * 
 * @author Backend Team
 * @version 1.0.0
 */
@Slf4j
public class ReactiveOperationTemplate {

    /**
     * 使用响应式上下文执行操作
     * 
     * 该方法提供基础模板，处理上下文传播和日志记录。
     * 适用于不需要特殊错误处理的简单操作。
     * 
     * 执行流程：
     * 1. 从 ContextView 中提取 traceId 并同步到 MDC
     * 2. 生成标准化的日志前缀
     * 3. 执行传入的业务逻辑
     * 4. 确保上下文正确传播到后续操作
     * 
     * @param operation 要执行的操作，接收 ContextualOperation 参数
     * @param <T> 操作返回的类型
     * @return 包装后的 Mono<T>，已处理上下文传播
     */
    public static <T> Mono<T> withTraceContext(Function<ContextualOperation<T>, Mono<T>> operation) {
        return Mono.deferContextual(contextView -> {
            // 同步 traceId 到 MDC 并获取日志前缀
            final String traceLogPrefix = LogUtils.prepareMdcAndGetPrefix(contextView);
            
            // 创建上下文操作对象并执行业务逻辑
            return operation.apply(new ContextualOperation<>(contextView, traceLogPrefix))
                    // 确保上下文正确传播到后续操作
                    .contextWrite(contextView);
        });
    }

    /**
     * 使用响应式上下文执行操作并处理错误
     * 
     * 该方法提供增强模板，除基础功能外还支持自定义错误处理。
     * 适用于需要特殊错误处理的操作。
     * 
     * 执行流程：
     * 1. 从 ContextView 中提取 traceId 并同步到 MDC
     * 2. 生成标准化的日志前缀
     * 3. 执行传入的业务逻辑
     * 4. 发生错误时应用自定义错误处理逻辑
     * 5. 确保上下文正确传播到后续操作
     * 
     * @param operation 要执行的操作，接收 ContextualOperation 参数
     * @param errorHandler 错误处理函数，接收异常并返回备用 Mono
     * @param <T> 操作返回的类型
     * @return 包装后的 Mono<T>，已处理上下文传播和错误处理
     */
    public static <T> Mono<T> withTraceContextAndErrorHandling(
            Function<ContextualOperation<T>, Mono<T>> operation,
            Function<Throwable, Mono<T>> errorHandler) {
        return withTraceContext(operation)
                .onErrorResume(errorHandler);
    }

    /**
     * 上下文操作封装类
     * 
     * 该类封装了响应式操作中常用的上下文信息，简化业务逻辑代码。
     * 作为业务逻辑函数的参数，提供统一的上下文访问接口。
     * 
     * 封装内容：
     * 1. ContextView - 访问响应式上下文
     * 2. traceLogPrefix - 标准化的日志前缀
     * 
     * 使用示例：
     * <pre>
     * ReactiveOperationTemplate.withTraceContext(op -> {
     *     log.info("{}执行业务操作", op.traceLogPrefix());
     *     return businessService.execute(op.contextView());
     * });
     * </pre>
     * 
     * @param <T> 操作返回的类型
     */
    public record ContextualOperation<T>(
            /**
             * 响应式上下文视图
             * 
             * 提供对当前响应式上下文的只读访问，
             * 可用于获取存储在上下文中的数据，如 traceId。
             * 
             * 使用场景：
             * - 获取 traceId 进行链路追踪
             * - 访问请求级别的上下文数据
             * - 传递给需要上下文的服务方法
             */
            ContextView contextView,
            
            /**
             * 标准化的日志前缀
             * 
             * 包含 traceId 的标准化日志前缀，格式为 "[traceId=xxx]"。
             * 用于统一日志格式，便于日志聚合和查询。
             * 
             * 格式示例：
             * - "[traceId=abc123]" - 有 traceId 的情况
             * - "[traceId=N/A]" - 无 traceId 的情况
             * 
             * 使用场景：
             * - 在日志中包含 traceId
             * - 标记操作的开始和结束
             * - 关联同一请求的多条日志
             */
            String traceLogPrefix
    ) {}
}