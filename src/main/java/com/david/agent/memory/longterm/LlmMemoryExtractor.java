package com.david.agent.memory.longterm;

import com.david.agent.agent.context.AgentContext;
import com.david.agent.agent.message.Message;
import com.david.agent.agent.message.MessageRole;
import com.david.agent.llm.LLMClient;
import com.david.agent.model.ChatResponse;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name = "agent.long-term-memory.enabled", havingValue = "true", matchIfMissing = true)
public class LlmMemoryExtractor implements MemoryExtractor {

    private static final String EXTRACTION_PROMPT = """
            你是一个 Memory Extractor。请从对话中提取值得长期保存的信息。

            必须提取（优先级最高）：
            1. 用户明确要求记住的信息（如"记住xxx"、"以后xxx"）—— 这类必须提取，不能遗漏

            其他提取：
            2. 用户稳定的个人偏好（如代码风格、语言偏好）
            3. 项目长期有效的信息（技术栈、架构、关键设计决策）
            4. 后续任务可能反复使用的重要事实

            不要提取：
            1. 普通闲聊、一次性问题、临时数据
            2. 实时行情、天气、当前时间、一次性错误
            3. 你推测出来但用户没有明确表达的信息
            4. 任何密码、Token、API Key、Cookie、Secret 或认证凭证（即使出现也禁止提取）

            key 命名规则（严格遵守）：
            - 英文小写 snake_case
            - 必须从以下风格中选取或类比，保证跨会话稳定：
              java_version, code_language, code_style, build_tool,
              project_stack, project_architecture, mcp_usage,
              tool_discovery, user_role, user_tech_stack, task_current_focus,
              user_name, user_preference, user_habit
            - 同一类信息永远用同一个 key，例如"Java 版本"永远用 java_version

            如果没有值得保存的信息，返回 []。

            输出严格 JSON 数组，不要 markdown 代码围栏：
            [{"type":"USER|PREFERENCE|PROJECT|FACT|TASK","key":"java_version","content":"用户代码示例统一使用 Java 21","importance":8}]
            """;

    private static final String RETRY_HINT = "\n\n你上次输出格式错误，请严格输出纯 JSON 数组，不要 markdown 围栏或任何多余文字。";

    private final LLMClient llmClient;
    private final ObjectMapper objectMapper;
    private final LongTermMemoryProperties properties;

    @Override
    public Mono<List<MemoryCandidate>> extract(ExtractionTurn turn) {
        if (turn == null || turn.userMessage() == null) {
            log.info("[memory-extractor] extract skipped: null turn or message");
            return Mono.just(List.of());
        }
        String answer = turn.finalAnswer();
        if (answer == null || answer.isBlank()) {
            log.info("[memory-extractor] extract skipped: blank finalAnswer");
            return Mono.just(List.of());
        }
        log.info("[memory-extractor] extracting, conversationId={}, userMsg='{}', answerLen={}",
                turn.conversationId(), abbreviate(turn.userMessage().content(), 50), answer.length());
        return extractOnce(turn, false)
                .timeout(properties.extraction().timeout())
                .doOnNext(candidates -> log.info("[memory-extractor] extraction result: {} candidate(s)", candidates.size()))
                .onErrorResume(error -> {
                    log.error("[memory-extractor] extraction failed, skip: {}", error.getMessage());
                    return Mono.just(List.of());
                });
    }

    private Mono<List<MemoryCandidate>> extractOnce(ExtractionTurn turn, boolean retried) {
        AgentContext context = buildContext(turn, retried);
        return llmClient.chat(context)
                .doOnNext(response -> log.debug("[memory-extractor] LLM raw response: {}",
                        abbreviate(response == null ? null : response.content(), 200)))
                .flatMap(response -> parseOrRetry(turn, response, retried));
    }

    private Mono<List<MemoryCandidate>> parseOrRetry(ExtractionTurn turn, ChatResponse response, boolean retried) {
        String text = response == null || response.content() == null ? "" : response.content();
        try {
            List<MemoryCandidate> candidates = parse(text);
            log.info("[memory-extractor] parsed {} candidate(s), retried={}", candidates.size(), retried);
            if (candidates.isEmpty()) {
                log.info("[memory-extractor] LLM returned empty list or unparseable types, text='{}'", abbreviate(text, 200));
            }
            return Mono.just(candidates);
        } catch (IllegalArgumentException error) {
            if (retried) {
                log.error("[memory-extractor] parse FAILED after retry, drop. output='{}', error='{}'",
                        abbreviate(text, 300), error.getMessage());
                return Mono.just(List.of());
            }
            log.warn("[memory-extractor] parse FAILED, retry once: {}, output='{}'", error.getMessage(), abbreviate(text, 300));
            return extractOnce(turn, true);
        }
    }

    private AgentContext buildContext(ExtractionTurn turn, boolean appendRetryHint) {
        String conversation = "用户：" + safe(turn.userMessage().content())
                + "\n助手：" + safe(turn.finalAnswer());
        List<Message> messages = List.of(
                Message.builder().role(MessageRole.SYSTEM).content(EXTRACTION_PROMPT).build(),
                Message.user(appendRetryHint ? conversation + RETRY_HINT : conversation)
        );
        return AgentContext.builder()
                .conversationId(turn.conversationId() == null ? "memory-extract" : turn.conversationId())
                .messages(messages)
                .tools(List.of())
                .build();
    }

    private List<MemoryCandidate> parse(String text) {
        JsonNode root;
        try {
            root = objectMapper.readTree(extractJsonArray(text));
        } catch (Exception error) {
            throw new IllegalArgumentException("invalid JSON: " + error.getMessage(), error);
        }
        if (!root.isArray()) {
            throw new IllegalArgumentException("expected JSON array");
        }
        List<MemoryCandidate> candidates = new ArrayList<>();
        for (JsonNode node : root) {
            MemoryCandidate candidate = toCandidate(node);
            if (candidate != null) {
                candidates.add(candidate);
            }
        }
        return List.copyOf(candidates);
    }

    private String extractJsonArray(String text) {
        String cleaned = text == null ? "" : text.trim();
        if (cleaned.startsWith("```")) {
            int newline = cleaned.indexOf('\n');
            cleaned = newline >= 0 ? cleaned.substring(newline + 1) : "";
            int fence = cleaned.lastIndexOf("```");
            if (fence >= 0) {
                cleaned = cleaned.substring(0, fence);
            }
        }
        int start = cleaned.indexOf('[');
        int end = cleaned.lastIndexOf(']');
        if (start < 0 || end <= start) {
            throw new IllegalArgumentException("no JSON array found");
        }
        return cleaned.substring(start, end + 1);
    }

    private MemoryCandidate toCandidate(JsonNode node) {
        if (node == null || !node.isObject()) {
            return null;
        }
        String typeText = node.path("type").asText("");
        MemoryType type;
        try {
            type = MemoryType.valueOf(typeText.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException error) {
            log.debug("[memory] unknown memory type in extraction output: {}", typeText);
            return null;
        }
        String content = node.path("content").asText("").trim();
        if (content.isEmpty()) {
            return null;
        }
        String key = node.path("key").asText("").trim();
        int importance = node.path("importance").asInt(5);
        importance = Math.max(1, Math.min(10, importance));
        return new MemoryCandidate(type, key, content, importance);
    }

    private String safe(String value) {
        return value == null ? "" : value;
    }

    private String abbreviate(String value, int maxLength) {
        if (value == null) return "";
        return value.length() <= maxLength ? value : value.substring(0, maxLength) + "...";
    }
}
