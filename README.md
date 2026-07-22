# Agent Server

面向桌面 Agent 的 Spring Boot 服务。当前实现可插拔 Agent Runtime、SSE 事件流、OpenAI Compatible LLM、Agent Loop、多 Tool 并行执行、PromptBuilder、MessageStore 和 Tool 拦截器链。

## 环境

- JDK 17
- Maven 3.9+（推荐）

请确认 `java -version` 和 `mvn -version` 都显示 Java 17。项目自带 Maven Central HTTPS 配置，依赖缓存位于项目的 `.m2` 目录。

## 启动

```bash
mvn spring-boot:run
```

## API

普通聊天：

```bash
curl -X POST http://localhost:8080/chat \
  -H "Content-Type: application/json" \
  -d '{"message":"hello"}'
```

演示工具调用（内置 `time` 工具）：

```bash
curl -X POST http://localhost:8080/chat \
  -H "Content-Type: application/json" \
  -d '{"message":"/time"}'
```

SSE 事件流：

```bash
curl -N -X POST http://localhost:8080/chat/stream \
  -H "Content-Type: application/json" \
  -H "Accept: text/event-stream" \
  -d '{"message":"/time"}'
```

响应会产生 iteration、LLM、tool 和 agent completed 事件。继续同一会话时，把完成事件中的 `conversationId` 放入下一次请求。

## 接入真实 LLM

默认使用无需密钥的 `DemoLLMClient`。接入 OpenAI Compatible 服务时设置：

```powershell
$env:LLM_PROVIDER="openrouter"
$env:LLM_BASE_URL="https://openrouter.ai/api/v1"
$env:LLM_API_KEY="your-key"
$env:LLM_MODEL="openai/gpt-5"
mvn spring-boot:run
```

DeepSeek、Qwen 和 Ollama 只需替换 `LLM_BASE_URL` 与 `LLM_MODEL`。Agent Loop 默认最多执行 8 次，可通过 `AGENT_MAX_ITERATIONS` 调整；工具默认 30 秒超时，可通过 `TOOL_TIMEOUT` 调整。
