package cn.finalscompass.ai.runtime.provider.client;

public final class RuntimeProviderClientException extends RuntimeException {
    private final String errorCode;
    private final Integer statusCode;
    private final boolean retryable;

    public RuntimeProviderClientException(String errorCode, Integer statusCode, boolean retryable, Throwable cause) {
        super("Runtime Provider request failed: " + errorCode, cause);
        this.errorCode = errorCode;
        this.statusCode = statusCode;
        this.retryable = retryable;
    }
    public String errorCode() { return errorCode; }
    public Integer statusCode() { return statusCode; }
    public boolean retryable() { return retryable; }
}
