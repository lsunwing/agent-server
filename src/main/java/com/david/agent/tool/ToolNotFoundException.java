package com.david.agent.tool;

public class ToolNotFoundException extends RuntimeException {

    public ToolNotFoundException(String toolName) {
        super("Tool not found: " + toolName);
    }
}
