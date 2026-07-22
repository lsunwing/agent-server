package com.david.agent.memory;

import com.david.agent.agent.message.Message;

import java.util.List;

public interface MessageStore {

    void append(String conversationId, Message message);

    default void appendAll(String conversationId, List<Message> messages) {
        messages.forEach(message -> append(conversationId, message));
    }

    List<Message> history(String conversationId);

    void clear(String conversationId);
}
