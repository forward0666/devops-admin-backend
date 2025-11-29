package exception;

/**
 * 无效请求异常：用于处理所有由于客户端请求参数、格式或校验失败导致的错误。
 * 通常由全局异常处理器捕获并返回 400 Bad Request。
 */
public class InvalidRequestException extends RuntimeException {

    public InvalidRequestException() {
        super("Invalid request parameters or format.");
    }

    public InvalidRequestException(String message) {
        super(message);
    }

    public InvalidRequestException(String message, Throwable cause) {
        super(message, cause);
    }
}