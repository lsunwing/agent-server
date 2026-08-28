package com.david.agent.tool.builtin;

import com.david.agent.memory.longterm.Memory;
import com.david.agent.memory.longterm.MemoryCandidate;
import com.david.agent.memory.longterm.MemoryManager;
import com.david.agent.memory.longterm.MemoryQuery;
import com.david.agent.memory.longterm.MemoryType;
import com.david.agent.tool.Tool;
import com.david.agent.tool.discovery.ToolMetadata;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name = "agent.long-term-memory.enabled", havingValue = "true", matchIfMissing = true)
public class MemoryTool implements Tool {

    private static final String SOURCE = "memory-tool";

    private final MemoryManager memoryManager;

    @Override
    public String name() {
        return "memory";
    }

    @Override
    public String description() {
        return "长期记忆管理工具。当用户要求记住某些信息、查询已记住的信息、或忘记某条记忆时调用。" +
                "action=save 需要提供 content（要记住的内容）；action=search 需要 query（检索词）；" +
                "action=forget 需要 memory_id（可先通过 search 获取）。";
    }

    @Override
    public ToolMetadata metadata() {
        return ToolMetadata.local(name(), description(), List.of(
                "记住", "记忆", "忘记", "偏好", "以后", "默认", "memory", "remember", "forget"
        ));
    }

    @Override
    public Map<String, Object> inputSchema() {
        Map<String, Object> properties = new LinkedHashMap<>();
        properties.put("action", Map.of(
                "type", "string",
                "enum", List.of("save", "search", "forget"),
                "description", "操作类型"
        ));
        properties.put("content", Map.of(
                "type", "string",
                "description", "action=save 时必填，如：以后代码统一使用 Java 21"
        ));
        properties.put("query", Map.of(
                "type", "string",
                "description", "action=search 时的检索词"
        ));
        properties.put("memory_id", Map.of(
                "type", "integer",
                "description", "action=forget 时必填"
        ));
        Map<String, Object> schema = new LinkedHashMap<>();
        schema.put("type", "object");
        schema.put("properties", properties);
        schema.put("required", List.of("action"));
        return schema;
    }

    @Override
    public Mono<Object> execute(Map<String, Object> arguments) {
        String action = string(arguments.get("action")).toLowerCase(Locale.ROOT);
        log.info("[memory-tool] action={}", action);
        return switch (action) {
            case "save" -> save(string(arguments.get("content")));
            case "search" -> search(string(arguments.get("query")));
            case "forget" -> forget(arguments.get("memory_id"));
            default -> Mono.just((Object) "无效的 action，只支持 save / search / forget");
        };
    }

    private Mono<Object> save(String content) {
        if (content.isBlank()) {
            return Mono.just((Object) "保存失败：content 不能为空");
        }
        MemoryCandidate candidate = new MemoryCandidate(MemoryType.FACT, "", content, 8);
        return memoryManager.saveCandidates(SOURCE, List.of(candidate))
                .map(saved -> {
                    if (saved.isEmpty()) {
                        return (Object) "未保存：内容为空、重要度不足或包含敏感信息（密码/密钥等禁止保存）";
                    }
                    Memory memory = saved.get(saved.size() - 1);
                    if (memory.sourceConversationId() == null || !SOURCE.equals(memory.sourceConversationId())) {
                        return (Object) "已存在相同记忆：" + format(memory);
                    }
                    return (Object) "已记住：" + format(memory);
                });
    }

    private Mono<Object> search(String query) {
        if (query.isBlank()) {
            return Mono.just((Object) "检索失败：query 不能为空");
        }
        return memoryManager.search(MemoryQuery.keyword(query.trim(), 10))
                .map(memories -> {
                    if (memories.isEmpty()) {
                        return (Object) "没有找到相关记忆";
                    }
                    StringBuilder sb = new StringBuilder("找到 ").append(memories.size()).append(" 条记忆：");
                    memories.forEach(memory -> sb.append('\n').append(format(memory)));
                    return (Object) sb.toString();
                });
    }

    private Mono<Object> forget(Object idArg) {
        Long id = toLong(idArg);
        if (id == null) {
            return Mono.just((Object) "忘记失败：memory_id 不能为空");
        }
        return memoryManager.forget(id)
                .thenReturn((Object) ("已忘记记忆 #" + id));
    }

    private String format(Memory memory) {
        return "[#" + memory.id() + "][" + memory.type() + "] "
                + (memory.memoryKey() == null ? "" : memory.memoryKey() + ": ")
                + memory.content();
    }

    private String string(Object value) {
        return value == null ? "" : String.valueOf(value).trim();
    }

    private Long toLong(Object value) {
        if (value instanceof Number number) {
            return number.longValue();
        }
        try {
            return value == null ? null : Long.parseLong(String.valueOf(value));
        } catch (NumberFormatException e) {
            return null;
        }
    }
}
