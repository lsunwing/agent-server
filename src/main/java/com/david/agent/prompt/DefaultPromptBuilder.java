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
    private static final String TOOL_USAGE_RULE = "你只能使用系统提供的工具列表中的工具。禁止调用未注册的工具（如 bash、shell、python、terminal、cmd 等）。如果现有工具无法完成任务，直接用你自身的知识回答。";

    private final PromptProperties properties;

    @Override
    public Prompt build(AgentContext context) {
        List<Message> messages = new ArrayList<>();
        String systemPrompt = properties.system();
        String mergedRules = TOOL_USAGE_RULE + "\n\n" + FINANCE_TOOL_RULE;
        if (!systemPrompt.isBlank()) {
            String merged = systemPrompt + "\n\n" + mergedRules;
            messages.add(Message.builder()
                    .role(MessageRole.SYSTEM)
                    .content(merged)
                    .build());
        } else {
            messages.add(Message.builder()
                    .role(MessageRole.SYSTEM)
                    .content(mergedRules)
                    .build());
        }
        messages.addAll(context.messages());
        return new Prompt(messages, context.tools());
    }
}
