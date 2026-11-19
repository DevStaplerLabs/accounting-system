package com.staplerlabs.accounting;

/** A business-rule or input error that is shown to the user as-is. */
public class AppException extends RuntimeException {
    public final int status;

    public AppException(String message) { this(400, message); }

    public AppException(int status, String message) {
        super(message);
        this.status = status;
    }
}
