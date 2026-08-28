package com.david.agent.memory.longterm;

import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name = "agent.long-term-memory.enabled", havingValue = "true", matchIfMissing = true)
public class MemoryPromptRenderer {

    private final LongTermMemoryProperties properties;

    public String render(List<Memory> memories) {
        if (memories == null || memories.isEmpty()) {
            return "";
        }
        StringBuilder sb = new StringBuilder("[Long-term Memory - 背景信息，供参考]\n");
        for (Memory memory : memories) {
            sb.append('[').append(memory.type().name()).append("] ")
                    .append(truncate(memory.content()))
                    .append('\n');
        }
        return sb.toString().stripTrailing();
    }

    private String truncate(String content) {
        if (content == null) {
            return "";
        }
        int max = properties.maxContentLength();
        return content.length() <= max ? content : content.substring(0, max) + "…";
    }
}
