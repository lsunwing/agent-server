package com.david.agent.controller;

import com.david.agent.agent.AgentLoopLimitException;
import com.david.agent.auth.UnauthorizedException;
import com.david.agent.tool.ToolNotFoundException;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.bind.support.WebExchangeBindException;

import java.util.Map;

@RestControllerAdvice
public class ApiExceptionHandler {

    @ExceptionHandler(UnauthorizedException.class)
    @ResponseStatus(HttpStatus.UNAUTHORIZED)
    public Map<String, String> handleUnauthorized(UnauthorizedException exception) {
        return Map.of("error", exception.getMessage());
    }

    @ExceptionHandler(WebExchangeBindException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public Map<String, String> handleValidation(WebExchangeBindException exception) {
        String message = exception.getAllErrors().isEmpty()
                ? "请求参数不合法"
                : exception.getAllErrors().get(0).getDefaultMessage();
        return Map.of("error", message);
    }

    @ExceptionHandler(ResponseStatusException.class)
    public Map<String, String> handleStatus(ResponseStatusException exception) {
        return Map.of("error", exception.getReason() == null ? "请求失败" : exception.getReason());
    }

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
