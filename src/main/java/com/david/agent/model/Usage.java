package com.david.agent.model;

import lombok.Builder;

@Builder
public record Usage(int promptTokens, int completionTokens, int totalTokens) {
}
