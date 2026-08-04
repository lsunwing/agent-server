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

    private static final String FINANCE_TOOL_RULE = "当用户询问股票价格、行情、走势时，优先调用finance相关工具。不要编造股票数据，必须基于工具返回结果回答。";

    private final PromptProperties properties;

    @Override
    public Prompt build(AgentContext context) {
        List<Message> messages = new ArrayList<>();
        String systemPrompt = properties.system();
        if (!systemPrompt.isBlank()) {
            String merged = systemPrompt + "\n\n" + FINANCE_TOOL_RULE;
            messages.add(Message.builder()
                    .role(MessageRole.SYSTEM)
                    .content(merged)
                    .build());
        } else {
            messages.add(Message.builder()
                    .role(MessageRole.SYSTEM)
                    .content(FINANCE_TOOL_RULE)
                    .build());
        }
        messages.addAll(context.messages());
        return new Prompt(messages, context.tools());
    }
}
