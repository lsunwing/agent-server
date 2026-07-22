package com.david.agent.prompt;

import com.david.agent.agent.context.AgentContext;
import com.david.agent.agent.message.Message;
import com.david.agent.agent.message.MessageRole;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

@Component
@RequiredArgsConstructor
public class DefaultPromptBuilder implements PromptBuilder {

    private final PromptProperties properties;

    @Override
    public Prompt build(AgentContext context) {
        List<Message> messages = new ArrayList<>();
        if (!properties.system().isBlank()) {
            messages.add(Message.builder()
                    .role(MessageRole.SYSTEM)
                    .content(properties.system())
                    .build());
        }
        messages.addAll(context.messages());
        return new Prompt(messages, context.tools());
    }
}
