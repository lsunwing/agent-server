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

## Weather Tool（新增）

内置工具新增 `weather`，可用于城市天气查询（当前为 mock 数据）。

示例：

```bash
curl -X POST http://localhost:8080/chat \
  -H "Content-Type: application/json" \
  -d '{"message":"帮我查一下上海天气"}'
```

也可直接查看工具列表：

```bash
curl http://localhost:8080/tools
```

## Reasoning Timeline（新增）

`/chat/stream` 现在会额外返回 `ReasoningTimelineEvent`，用于用户可理解的 Agent 推理状态展示，例如：

- Agent正在思考问题
- 正在调用天气工具
- 天气工具返回
- 正在整理答案

事件结构示例：

```json
{
  "type": "ReasoningTimelineEvent",
  "conversationId": "...",
  "timestamp": "2026-07-24T09:30:00Z",
  "iteration": 0,
  "stage": "TOOL_START",
  "message": "正在调用weather工具"
}
```

## GitHub MCP Server 接入（新增）

项目新增了 MCP 客户端接入层，可把 GitHub MCP Server 的动态工具注册进现有 ToolRegistry。

### 启用方式

默认关闭，启用时设置：

```powershell
$env:MCP_GITHUB_ENABLED="true"
$env:MCP_GITHUB_COMMAND="npx"
$env:MCP_GITHUB_PACKAGE="@modelcontextprotocol/server-github"
```

如需指定工作目录：

```powershell
$env:MCP_GITHUB_WORKING_DIR="D:\apps\mcpserver\github-mcp-server"
```

启动后会自动：

1. 启动 GitHub MCP Server（stdio）
2. 执行 `initialize`
3. 执行 `tools/list`
4. 将发现的 MCP tools 动态注册到 ToolRegistry

你可以通过 `GET /tools` 查看是否已出现 `github_*` 工具。

### MCP 状态排障接口

新增：

```bash
curl http://localhost:8080/mcp/status
```

返回关键信息：

- `enabled`: 是否启用 MCP github
- `initialized`: 是否完成 MCP initialize
- `processAlive`: MCP 子进程是否存活
- `discoveredTools`: 已发现的 MCP tools 名称
- `lastError`: 最近一次 MCP 错误（为空表示最近无错误）

## Tool Discovery（新增）

Agent 在进入 `AgentLoop` 前会执行 Tool Discovery，不再默认把全部工具发送给 LLM。

新增模块：

- `ToolMetadata`：描述工具来源与关键词能力
- `ToolDescriptor`：统一 Local / MCP / Remote API 工具描述
- `ToolCatalog`：汇总工具描述
- `ToolRetriever`：可扩展检索接口
- `SimpleKeywordRetriever`：基于关键词的默认检索器
- `ToolDiscoveryService`：执行检索并过滤 `AgentContext.tools`

当前流程：

1. 用户消息进入 `AgentLoop`
2. `ToolDiscoveryService` 根据用户意图筛选工具
3. 仅将相关工具注入 `AgentContext.tools`
4. `LLMClient` 发起推理与 tool-calls

## 财经 Tool（新增）

新增了财经股票历史行情工具链（当前默认 mock provider）：

- `StockTool`：`stock_history`
- `StockService`：股票查询业务逻辑
- `FinanceProvider`：财经数据源抽象
- `MockSinaFinanceProvider`：默认实现（可替换为真实新浪/东财等）

示例请求：

```bash
curl -X POST http://localhost:8080/chat \
  -H "Content-Type: application/json" \
  -d '{"message":"沃特股份昨天收盘价是多少？"}'
```

工具调用参数示例：

```json
{
  "code": "002886",
  "date": "2026-07-28"
}
```

### SinaFinanceClient 真实接口接入

已实现 `SinaFinanceClient` 与 `SinaFinanceProvider`（默认关闭）。

启用方式：

```powershell
$env:FINANCE_SINA_ENABLED="true"
```

可选配置：

```powershell
$env:FINANCE_SINA_BASE_URL="https://money.finance.sina.com.cn"
$env:FINANCE_SINA_KLINE_PATH="/quotes_service/api/json_v2.php/CN_MarketData.getKLineData"
$env:FINANCE_SINA_SCALE="240"
$env:FINANCE_SINA_MAX_DATALEN="200"
$env:FINANCE_SINA_TIMEOUT="8s"
```

关闭时自动回落到 `MockSinaFinanceProvider`。
