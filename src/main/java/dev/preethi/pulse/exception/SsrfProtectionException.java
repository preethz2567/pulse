package dev.preethi.pulse.exception;

public class SsrfProtectionException extends RuntimeException {

    public SsrfProtectionException(String message) {
        super(message);
    }
}
