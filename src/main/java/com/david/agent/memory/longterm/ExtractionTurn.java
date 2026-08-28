package com.david.agent.memory.longterm;

import com.david.agent.agent.message.Message;

public record ExtractionTurn(
        String conversationId,
        Message userMessage,
        String finalAnswer
) {
}
