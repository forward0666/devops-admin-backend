package exception;

import java.util.concurrent.TimeoutException;

/**
 * 积极超时异常：用于 Webhook 等需要快速响应的路径。
 * 当请求处理时间超过预设的极短时间限制时抛出。
 * 它继承自 TimeoutException，但用于区分业务逻辑超时和 Webhook 策略超时。
 */
public class AggressiveTimeoutException extends TimeoutException {

    public AggressiveTimeoutException() {
        super("Aggressive Webhook Timeout");
    }

    public AggressiveTimeoutException(String message) {
        super(message);
    }

    public AggressiveTimeoutException(String message, Throwable cause) {
        super(message);
        this.initCause(cause);
    }
}