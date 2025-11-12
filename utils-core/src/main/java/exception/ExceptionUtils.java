package exception;

import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;

/**
 * ExceptionUtils - 统一处理中间件或外部依赖异常
 * 仅打印简短信息，可绑定 traceId
 */
@Slf4j
public class ExceptionUtils {

    /**
     * 打印异常日志，不带堆栈
     *
     * @param traceId traceId，用于关联业务
     * @param message 异常说明
     */
    public static void logSimple(String traceId, String message) {
        if (traceId != null) {
            MDC.put("traceId", traceId);
        }
        try {
            log.error("[traceId={}] {}", traceId, message);
        } finally {
            if (traceId != null) {
                MDC.remove("traceId");
            }
        }
    }

    /**
     * 打印异常日志及异常对象（可选，默认不打印堆栈）
     *
     * @param traceId traceId
     * @param message 异常说明
     * @param e       异常对象
     */
    public static void logSimple(String traceId, String message, Throwable e) {
        if (traceId != null) {
            MDC.put("traceId", traceId);
        }
        try {
            log.error("[traceId={}] {}", traceId, message);
        } finally {
            if (traceId != null) {
                MDC.remove("traceId");
            }
        }
    }

    /**
     * 抛出自定义运行时异常，可统一 catch
     */
    public static RuntimeException wrapRuntime(String traceId, String message, Throwable e) {
        logSimple(traceId, message, e);
        return new RuntimeException(message);
    }
}
