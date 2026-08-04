package com.david.agent.memory;

import com.david.agent.agent.message.Message;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

@Component
@ConditionalOnProperty(name = "agent.memory.type", havingValue = "memory")
public class InMemoryMessageStore implements MessageStore {

    private final ConcurrentHashMap<String, CopyOnWriteArrayList<Message>> conversations =
            new ConcurrentHashMap<>();

    @Override
    public void append(String conversationId, Message message) {
        conversations.computeIfAbsent(conversationId, ignored -> new CopyOnWriteArrayList<>()).add(message);
    }

    @Override
    public List<Message> history(String conversationId) {
        return List.copyOf(conversations.getOrDefault(conversationId, new CopyOnWriteArrayList<>()));
    }

    @Override
    public void clear(String conversationId) {
        conversations.remove(conversationId);
    }
}
