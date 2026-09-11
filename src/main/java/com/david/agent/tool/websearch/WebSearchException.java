package com.david.agent.tool.websearch;

public class WebSearchException extends RuntimeException {

    public WebSearchException(String message) {
        super(message);
    }

    public WebSearchException(String message, Throwable cause) {
        super(message, cause);
    }
}
