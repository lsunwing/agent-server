package com.david.agent.log;

import lombok.extern.slf4j.Slf4j;

/**
 * 问答全链路流程日志。统一 [flow] 前缀，方便按阶段过滤：
 * START / ENRICH / TOOL_SELECT / PROMPT / LLM / TOOL_EXEC / ANSWER / ERROR
 */
@Slf4j
public final class AgentFlowLogger {

    private AgentFlowLogger() {
    }

    public static void start(String conversationId, String query) {
        log.info("[flow][START] conversationId={} query='{}'", conversationId, abbreviate(query, 80));
    }

    public static void enrich(String conversationId, int memories, int ragChunks, String skill, String ragSources) {
        log.info("[flow][ENRICH] conversationId={} memories={} ragChunks={} skill={} ragSources={}",
                conversationId, memories, ragChunks, skill == null ? "-" : skill,
                ragSources == null || ragSources.isBlank() ? "-" : ragSources);
        if (ragChunks > 0) {
            log.info("[flow][ENRICH] 路径=知识库RAG 将把片段注入系统提示，优先据此回答");
        } else {
            log.info("[flow][ENRICH] 路径=无RAG命中，依赖技能/工具/大模型自身知识");
        }
    }

    public static void toolSelect(String conversationId, String reason, java.util.List<String> toolNames) {
        log.info("[flow][TOOL_SELECT] conversationId={} reason={} tools={}",
                conversationId, reason, toolNames);
    }

    public static void prompt(String conversationId, boolean hasRag, boolean hasSkill, int toolCount, int historyTurns) {
        log.info("[flow][PROMPT] conversationId={} ragInjected={} skillInjected={} tools={} historyMessages={}",
                conversationId, hasRag, hasSkill, toolCount, historyTurns);
    }

    public static void llmRequest(String conversationId, int iteration, String model, int messageCount, int toolCount) {
        log.info("[flow][LLM] conversationId={} iteration={} model={} messages={} tools={}",
                conversationId, iteration, model, messageCount, toolCount);
    }

    public static void llmResponse(String conversationId, int iteration, String finishReason,
                                   int toolCallCount, int contentLen, boolean hasContent) {
        String path = toolCallCount > 0
                ? "工具调用(MCP/Tool)"
                : (hasContent ? "直接生成回答(大模型)" : "空回复");
        log.info("[flow][LLM] conversationId={} iteration={} finishReason={} toolCalls={} contentLen={} => 下一步={}",
                conversationId, iteration, finishReason, toolCallCount, contentLen, path);
    }

    public static void toolExec(String conversationId, int iteration, String toolName, String source, String argsPreview) {
        log.info("[flow][TOOL_EXEC] conversationId={} iteration={} tool={} source={} args={}",
                conversationId, iteration, toolName, source, abbreviate(argsPreview, 120));
    }

    public static void toolDone(String conversationId, int iteration, String toolName, boolean ok, String resultPreview) {
        log.info("[flow][TOOL_EXEC] conversationId={} iteration={} tool={} ok={} result={}",
                conversationId, iteration, toolName, ok, abbreviate(resultPreview, 120));
    }

    public static void answer(String conversationId, int iteration, int contentLen, String finishReason) {
        log.info("[flow][ANSWER] conversationId={} iteration={} contentLen={} finishReason={} 路径=最终回答",
                conversationId, iteration, contentLen, finishReason);
    }

    public static void error(String conversationId, String stage, String message) {
        log.error("[flow][ERROR] conversationId={} stage={} message={}", conversationId, stage, message);
    }

    private static String abbreviate(String value, int max) {
        if (value == null) {
            return "";
        }
        String flat = value.replace('\n', ' ').replace('\r', ' ');
        return flat.length() <= max ? flat : flat.substring(0, max) + "...";
    }
}
