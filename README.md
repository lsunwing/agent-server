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

## MCP Server 接入（支持多 Server）

项目支持同时接入多个 MCP Server，通过 `mcp.*` 配置前缀动态注册。

### 配置方式

在 `application.yml` 中配置多个 MCP Server：

```yaml
mcp:
  servers:
    github:
      enabled: true
      command: go
      workingDirectory: D:/workspace/mcpserver/github-mcp-server
      args:
        - run
        - ./cmd/github-mcp-server
        - stdio
    filesystem:
      enabled: true
      command: npx
      workingDirectory: D:/workspace
      args:
        - "-y"
        - "@modelcontextprotocol/server-filesystem"
        - "D:/workspace"
```

### 配置项说明

每个 MCP Server 支持以下配置：

| 字段 | 类型 | 说明 |
|------|------|------|
| `enabled` | boolean | 是否启用该 MCP Server |
| `command` | string | 启动命令（如 `npx`、`go`、`java`） |
| `workingDirectory` | string | 工作目录（可选） |
| `args` | list | 命令参数列表 |
| `startupTimeout` | duration | 启动超时时间，默认 10s |

### 启动后自动执行

1. 启动所有 `enabled: true` 的 MCP Server（stdio）
2. 执行 `initialize` 握手
3. 执行 `tools/list` 发现工具
4. 将发现的 MCP tools 动态注册到 ToolRegistry

### MCP 状态排障接口

```bash
curl http://localhost:8080/mcp/status
```

返回所有 MCP Server 的状态列表，每个包含：

- `serverName`: 服务名称（如 `github`、`filesystem`）
- `enabled`: 是否启用
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

## SQLite 持久化（新增）

对话记忆支持 SQLite 持久化，重启服务后对话历史不丢失。

默认启用，数据库文件生成在项目根目录 `./agent.db`。

### 配置项

| 环境变量 | 默认值 | 说明 |
|---------|--------|------|
| `AGENT_MEMORY_TYPE` | `sqlite` | 记忆实现类型，可选 `sqlite` / `memory` |
| `AGENT_DB_PATH` | `./agent.db` | SQLite 数据库文件路径 |

### 使用示例

```powershell
# 默认 SQLite 持久化，无需额外配置
mvn spring-boot:run

# 指定数据库路径
$env:AGENT_DB_PATH=".\projectName\agent.db"
mvn spring-boot:run

# 切回内存模式（重启丢失）
$env:AGENT_MEMORY_TYPE="memory"
mvn spring-boot:run
```

### 数据表结构

启动时自动建表，无需手动初始化：

```sql
CREATE TABLE chat_message (
    id              INTEGER PRIMARY KEY AUTOINCREMENT,
    conversation_id TEXT    NOT NULL,
    seq             INTEGER NOT NULL,
    role            TEXT    NOT NULL,       -- USER / ASSISTANT / TOOL / SYSTEM
    content         TEXT,
    name            TEXT,
    tool_call_id    TEXT,
    tool_calls      TEXT,                   -- JSON 格式的 ToolCall 列表
    created_at      TEXT    NOT NULL DEFAULT (datetime('now')),
    UNIQUE(conversation_id, seq)
);
```

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
  -d '{"message":"工商银行昨天收盘价是多少？"}'
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

## Long-term Memory（长期记忆）

Agent 具备长期记忆能力，能从对话中自动提取值得记住的信息，并在后续对话中检索注入，使 Agent 真正"记住"用户偏好、项目信息等长期有效内容。

### 工作原理

```
用户对话 → Agent 回答 → 异步提取记忆(LLM) → 去重/冲突处理 → 落库(SQLite)
下次对话 ← 检索相关记忆 ← 注入 SYSTEM 消息 ← 分层检索策略
```

**两条写入路径**：

1. **自动提取**：对话结束后异步触发 LLM 提取，无需用户显式要求
2. **显式保存**：用户说"记住 xxx"时，Agent 调用 `memory` 工具立即保存

### 记忆类型

| 类型 | 含义 | 示例 |
|------|------|------|
| `USER` | 用户稳定事实 | 用户主要使用 Java 开发 |
| `PREFERENCE` | 长期偏好 | 代码示例默认 Java 21 |
| `PROJECT` | 项目信息 | Agent 使用 Java + Spring Boot |
| `FACT` | 长期有效事实 | 某 MCP Server 的用途 |
| `TASK` | 简单任务上下文 | 正在开发股票 Tool |

### 配置

```yaml
agent:
  long-term-memory:
    enabled: ${AGENT_LTM_ENABLED:true}              # 整体开关
    max-inject: ${AGENT_LTM_MAX_INJECT:8}           # 单次注入上限
    max-content-length: ${AGENT_LTM_MAX_LEN:200}    # 单条截断长度
    extraction:
      enabled: ${AGENT_LTM_EXTRACT_ENABLED:true}    # 自动提取开关
      gate-enabled: ${AGENT_LTM_GATE_ENABLED:true}  # 信号词门控(省钱)
      min-importance: ${AGENT_LTM_MIN_IMPORTANCE:4} # 低于此丢弃
      timeout: ${AGENT_LTM_EXTRACT_TIMEOUT:15s}     # LLM 提取超时
```

环境变量示例：

```powershell
# 关闭长期记忆
$env:AGENT_LTM_ENABLED="false"

# 关闭自动提取（仅保留 memory 工具显式保存）
$env:AGENT_LTM_EXTRACT_ENABLED="false"

# 关闭信号词门控（所有对话都触发提取，更全面但更贵）
$env:AGENT_LTM_GATE_ENABLED="false"
```

### 使用示例

**显式记忆**（通过对话触发 memory 工具）：

```bash
# 让 Agent 记住偏好
curl -X POST http://localhost:8080/chat \
  -H "Content-Type: application/json" \
  -d '{"message":"以后代码示例都用 Java 21，记住我的名字叫 David"}'

# 查询已记住的信息
curl -X POST http://localhost:8080/chat \
  -H "Content-Type: application/json" \
  -d '{"message":"你还记得我的名字吗？"}'
```

**自动提取**：对话中提到"以后用 Java 21"、"我喜欢简洁风格"等，Agent 回答后异步提取并存储。

### 记忆工具

Agent 内置 `memory` 工具，支持三个 action：

| action | 参数 | 说明 |
|--------|------|------|
| `save` | `content` | 保存一条记忆 |
| `search` | `query` | 检索相关记忆 |
| `forget` | `memory_id` | 归档（忘记）一条记忆 |

### 数据表结构

启动时自动建表：

```sql
CREATE TABLE long_term_memory (
    id                     INTEGER PRIMARY KEY AUTOINCREMENT,
    type                   TEXT    NOT NULL,        -- USER/PREFERENCE/PROJECT/FACT/TASK
    memory_key             TEXT,                    -- 如 java_version, user_name
    content                TEXT    NOT NULL,         -- 记忆内容
    importance             INTEGER NOT NULL DEFAULT 5, -- 1-10
    status                 TEXT    NOT NULL DEFAULT 'ACTIVE', -- ACTIVE/ARCHIVED
    source_conversation_id TEXT,                    -- 来源会话 ID
    metadata               TEXT,
    created_at             TEXT    NOT NULL DEFAULT (datetime('now')),
    updated_at             TEXT    NOT NULL DEFAULT (datetime('now'))
);
```

### 去重与冲突处理

- 内容完全相同 → 跳过不重复写入
- 相同 key → 旧记录标记 `ARCHIVED`，插入新记录
- 内容相似度（Jaccard）≥ 0.5 → 同上，沿用旧 key 保持稳定
- 同类型始终只有一条 `ACTIVE` 记录

### 安全

敏感信息（API Key、密码、Token 等）会被正则拦截，不会写入记忆库。`memory` 工具的 `save` action 和自动提取都有此保护。
