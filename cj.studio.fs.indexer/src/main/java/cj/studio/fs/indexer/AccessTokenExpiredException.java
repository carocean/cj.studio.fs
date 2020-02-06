package cj.studio.fs.indexer;

public class AccessTokenExpiredException extends Exception {
    public AccessTokenExpiredException() {
        super();
    }

    public AccessTokenExpiredException(String message) {
        super(message);
    }

    public AccessTokenExpiredException(String message, Throwable cause) {
        super(message, cause);
    }

    public AccessTokenExpiredException(Throwable cause) {
        super(cause);
    }

    protected AccessTokenExpiredException(String message, Throwable cause, boolean enableSuppression, boolean writableStackTrace) {
        super(message, cause, enableSuppression, writableStackTrace);
    }
}
