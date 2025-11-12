package network;

import jakarta.validation.constraints.NotNull;
import org.slf4j.MDC;
import org.springframework.web.server.ServerWebExchange;

import java.util.*;
import java.util.concurrent.*;
import java.util.function.Supplier;

/**
 * 全链路 traceId 工具类
 * 自动处理：
 * 1. 获取 traceId（优先 CF-RAY -> X-Trace-Id -> UUID）
 * 2. 设置 MDC
 * 3. 将 traceId 传递给下游请求头 X-Trace-Id
 * 4. 支持异步线程 MDC 传递
 */
public class TraceIdUtils {

    private TraceIdUtils() {}

    /** 获取全链路 traceId（优先 CF-RAY -> X-Trace-Id -> UUID） */
    public static String getTraceId(ServerWebExchange exchange) {
        String cfRay = ClientHeaderUtils.getClientHeader(exchange, "CF-RAY");
        if (cfRay != null && !cfRay.isBlank()) return cfRay;

        String xTraceId = ClientHeaderUtils.getClientHeader(exchange, "X-Trace-Id");
        if (xTraceId != null && !xTraceId.isBlank()) return xTraceId;

        return UUID.randomUUID().toString();
    }

    /** 设置 MDC 并返回带 X-Trace-Id 的下游请求 Exchange */
    public static ServerWebExchange enrichExchange(ServerWebExchange exchange) {
        String traceId = getTraceId(exchange);
        setTraceId(traceId);
        return exchange.mutate()
                .request(r -> r.header("X-Trace-Id", traceId))
                .build();
    }

    /** 设置 MDC traceId */
    public static void setTraceId(String traceId) {
        MDC.put("traceId", traceId);
    }

    /** 清理 MDC traceId */
    public static void clearMdc() {
        MDC.remove("traceId");
    }

    /** 包装 Runnable，保证 MDC 能在异步线程中传递 */
    public static Runnable wrapRunnable(Runnable runnable) {
        Map<String, String> contextMap = MDC.getCopyOfContextMap();
        return () -> {
            if (contextMap != null) MDC.setContextMap(contextMap);
            try { runnable.run(); }
            finally { MDC.clear(); }
        };
    }

    /** 包装 Supplier，保证 MDC 能在异步线程中传递 */
    public static <T> Supplier<T> wrapSupplier(Supplier<T> supplier) {
        Map<String, String> contextMap = MDC.getCopyOfContextMap();
        return () -> {
            if (contextMap != null) MDC.setContextMap(contextMap);
            try { return supplier.get(); }
            finally { MDC.clear(); }
        };
    }

    /** 创建带 MDC 传递能力的 ExecutorService */
    public static ExecutorService mdcExecutor(ExecutorService delegate) {
        return new MdcExecutorService(delegate);
    }

    /** 包装 ExecutorService，使 MDC 能够自动传递 */
    public static class MdcExecutorService implements ExecutorService {

        private final ExecutorService delegate;

        public MdcExecutorService(ExecutorService delegate) {
            this.delegate = delegate;
        }

        private <T> Callable<T> wrapCallable(Callable<T> task) {
            Map<String, String> contextMap = MDC.getCopyOfContextMap();
            return () -> {
                if (contextMap != null) MDC.setContextMap(contextMap);
                try { return task.call(); }
                finally { MDC.clear(); }
            };
        }

        @Override
        public void execute(@NotNull Runnable command) {
            delegate.execute(wrapRunnable(command));
        }

        @Override
        public <T> Future<T> submit(@NotNull Callable<T> task) {
            return delegate.submit(wrapCallable(task));
        }

        @Override
        public <T> Future<T> submit(@NotNull Runnable task, T result) {
            return delegate.submit(wrapRunnable(task), result);
        }

        @Override
        public Future<?> submit(@NotNull Runnable task) {
            return delegate.submit(wrapRunnable(task));
        }

        @Override
        public void shutdown() { delegate.shutdown(); }

        @NotNull
        @Override
        public List<Runnable> shutdownNow() { return delegate.shutdownNow(); }

        @Override
        public boolean isShutdown() { return delegate.isShutdown(); }

        @Override
        public boolean isTerminated() { return delegate.isTerminated(); }

        @Override
        public boolean awaitTermination(long timeout, @NotNull TimeUnit unit) throws InterruptedException {
            return delegate.awaitTermination(timeout, unit);
        }

        @NotNull
        @Override
        public <T> List<Future<T>> invokeAll(@NotNull Collection<? extends Callable<T>> tasks)
                throws InterruptedException {
            List<Callable<T>> wrappedTasks = tasks.stream().map(this::wrapCallable).toList();
            return delegate.invokeAll(wrappedTasks);
        }

        @NotNull
        @Override
        public <T> List<Future<T>> invokeAll(@NotNull Collection<? extends Callable<T>> tasks,
                                             long timeout, @NotNull TimeUnit unit)
                throws InterruptedException {
            List<Callable<T>> wrappedTasks = tasks.stream().map(this::wrapCallable).toList();
            return delegate.invokeAll(wrappedTasks, timeout, unit);
        }

        @Override
        public <T> T invokeAny(@NotNull Collection<? extends Callable<T>> tasks)
                throws InterruptedException, ExecutionException {
            List<Callable<T>> wrappedTasks = tasks.stream().map(this::wrapCallable).toList();
            return delegate.invokeAny(wrappedTasks);
        }

        @Override
        public <T> T invokeAny(@NotNull Collection<? extends Callable<T>> tasks,
                               long timeout, @NotNull TimeUnit unit)
                throws InterruptedException, ExecutionException, TimeoutException {
            List<Callable<T>> wrappedTasks = tasks.stream().map(this::wrapCallable).toList();
            return delegate.invokeAny(wrappedTasks, timeout, unit);
        }
    }
}
