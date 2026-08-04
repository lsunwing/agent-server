package com.david.agent.tool.finance.exception;

public class FinanceApiException extends RuntimeException {

    public FinanceApiException(String message) {
        super(message);
    }

    public FinanceApiException(String message, Throwable cause) {
        super(message, cause);
    }
}
