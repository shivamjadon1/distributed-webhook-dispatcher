package com.web3task.webhook.exception;

public class SSRFSecurityException extends RuntimeException {

    public SSRFSecurityException(String message) {
        super(message);
    }
}
