package com.david.agent.agent;

public class AgentLoopLimitException extends RuntimeException {

    public AgentLoopLimitException(int maxIterations) {
        super("Agent exceeded the maximum of " + maxIterations + " iterations");
    }
}
