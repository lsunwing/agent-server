package com.david.agent.controller;

import com.david.agent.agent.AgentLoopLimitException;
import com.david.agent.tool.ToolNotFoundException;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.Map;

@RestControllerAdvice
public class ApiExceptionHandler {

    @ExceptionHandler(ToolNotFoundException.class)
    @ResponseStatus(HttpStatus.NOT_FOUND)
    public Map<String, String> handleToolNotFound(ToolNotFoundException exception) {
        return Map.of("error", exception.getMessage());
    }

    @ExceptionHandler(AgentLoopLimitException.class)
    @ResponseStatus(HttpStatus.UNPROCESSABLE_ENTITY)
    public Map<String, String> handleLoopLimit(AgentLoopLimitException exception) {
        return Map.of("error", exception.getMessage());
    }
}
