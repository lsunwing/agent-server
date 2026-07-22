package com.david.agent.prompt;

import com.david.agent.agent.context.AgentContext;

public interface PromptBuilder {

    Prompt build(AgentContext context);
}
