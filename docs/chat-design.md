# Chat 对话引擎 — 设计与实现状态

> 状态：核心链路（sendMessage 双端点）**已实现并端到端验证**（真实 DeepSeek 调用）
> 更新：2026-09-17

## 范围

对话引擎负责：会话管理、流式对话（SSE）、上下文窗口管理。依赖 `hify-agent`（Agent 配置）与 `hify-provider`（适配层）。

本期不做：RAG 检索接入、工作流触发、消息反馈、多模态内容。

---

## 1. 数据模型

### 1.1 表清单：2 张

`chat_session` + `chat_message`。工具调用、RAG 引用等放 `chat_message.meta`，不建独立表。

### 1.2 DDL

```sql
-- chat_session: one conversation with an agent
CREATE TABLE IF NOT EXISTS chat_session (
  id              BIGINT       NOT NULL AUTO_INCREMENT COMMENT 'Primary key',
  agent_id        BIGINT       NOT NULL COMMENT 'agent.id (app-layer FK)',
  title           VARCHAR(128) NULL COMMENT 'Derived from the first user message',
  status          VARCHAR(20)  NOT NULL DEFAULT 'ACTIVE' COMMENT 'ACTIVE / ARCHIVED',
  last_message_at DATETIME     NULL COMMENT 'Last activity, for list ordering',
  message_count   INT          NOT NULL DEFAULT 0 COMMENT 'Messages in the session',
  created_at      DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT 'Creation time',
  updated_at      DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT 'Last update time',
  deleted         TINYINT      NOT NULL DEFAULT 0 COMMENT 'Logical delete: 0 = normal, 1 = deleted',
  PRIMARY KEY (id),
  KEY idx_chat_session_agent_id (agent_id),
  KEY idx_chat_session_last_message_at (last_message_at)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci
  COMMENT = 'Chat sessions';

-- chat_message: a single message (fastest growing table; NO logical delete on
-- purpose — rows are physically removed with their session)
CREATE TABLE IF NOT EXISTS chat_message (
  id                BIGINT      NOT NULL AUTO_INCREMENT COMMENT 'Primary key',
  session_id        BIGINT      NOT NULL COMMENT 'chat_session.id (app-layer FK)',
  parent_id         BIGINT      NULL COMMENT 'Message being replied to; NULL = first message',
  role              VARCHAR(16) NOT NULL COMMENT 'user | assistant | system | tool',
  content           LONGTEXT    NOT NULL COMMENT 'Message text',
  status            VARCHAR(16) NOT NULL DEFAULT 'COMPLETED' COMMENT 'GENERATING / COMPLETED / FAILED',
  finish_reason     VARCHAR(32) NULL COMMENT 'stop | length | tool_calls | cancelled | ...',
  prompt_tokens     INT         NOT NULL DEFAULT 0 COMMENT 'Prompt tokens (0 when unreported)',
  completion_tokens INT         NOT NULL DEFAULT 0 COMMENT 'Completion tokens (0 when unreported)',
  meta              JSON        NULL COMMENT 'tool_calls / tool_call_id / RAG citations',
  created_at        DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT 'Creation time',
  updated_at        DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT 'Last update time',
  PRIMARY KEY (id),
  KEY idx_chat_message_session_id_id (session_id, id),
  KEY idx_chat_message_parent_id (parent_id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci
  COMMENT = 'Chat messages';
```

### 1.3 决策记录

| 决策点 | 采纳 | 被否决方案与取舍 |
|---|---|---|
| 流式落库 | **先占位后更新**：INSERT 空消息（GENERATING）→ 流结束 UPDATE（COMPLETED + content + usage）；异常标 FAILED | ① 一次性写入：流中断即丢整条回复；② 周期 UPDATE：大表写放大 |
| chat_message 逻辑删除 | **去掉 `deleted`** | 保留：所有查询带 `WHERE deleted=0`，索引变宽。先例：`provider_health` |
| 工具调用存储 | **放 `meta` JSON** | 独立表：MVP 用不上；工具调用统计属 MCP 模块后续议题 |
| 重新生成 | **加 `parent_id` 预留** | 不加：以后补字段需迁移已有数据 |

**`parent_id` 语义**：指向它所回复的那条消息。user 消息 → 上一轮 assistant（首条为 NULL）；assistant 消息 → 触发它的 user。重新生成时同一 `parent_id` 下多条 assistant，`id` 最大者为当前分支。

### 1.4 存储与上下文策略

- **会话 / 消息**：MySQL 全量持久化，不缓存（source of truth）
- **上下文窗口**：Redis `session:{sessionId}`，TTL 2h（`hify.chat.context-ttl`），存最近 `agent.max_context_turns` 轮
  - 缓存 miss 时从 MySQL 按 `(session_id, id)` 重建（只取 `status = COMPLETED` 的消息）
  - **截断按完整轮次边界**：从末尾数第 N 条 user 消息处切，避免 tool_calls 与其 tool 结果被切一半
- **分页**：会话列表普通分页（按 `last_message_at DESC`）；消息列表用游标分页（`id < cursor` 倒序），不做 COUNT(*)
- **计数维护**：`message_count` 在消息 INSERT 时 +1；`last_message_at` 同步刷新

### 1.5 实体层注意

`chat_message` 无 `deleted` → **不继承 `BaseEntity`**（`@TableLogic` 会生成 `WHERE deleted=0` 直接报错），照 `ProviderHealth` 写法用独立实体。`meta` 列需要 `@TableField(typeHandler = JacksonTypeHandler.class)` **配合** `@TableName(autoResultMap = true)`，否则 insert 正常但 select 读不出。

---

## 2. 传输层：SSE（而非 WebSocket）

对话是"发一条 → 流式收一条"的请求-响应模型，单向推送足够；鉴权/代理/网关全部复用 HTTP。WebSocket 的全双工能力在此场景无用武之地，却要付出连接注册表、心跳、重连、帧协议设计的成本。行业标准亦如此（OpenAI / Anthropic 流式 API、Dify 均为 SSE）。CLAUDE.md 已定调。

被否决：WebSocket（双向能力过剩、浏览器 WS API 无法带自定义 header 鉴权、Nginx/K8s Ingress 需额外配置）。

---

## 3. 接口契约

### 3.1 端点

| 方法 | 路径 | 说明 | 状态 |
|---|---|---|---|
| POST | `/api/v1/chat-sessions` | **创建会话**（JSON，非流式） | ✅ 已实现 |
| POST | `/api/v1/chat-sessions/{id}/messages` | **发消息**，SSE 流式响应 | ✅ 已实现 |
| GET | `/api/v1/chat-sessions` | 会话列表（分页，`last_message_at DESC`） | ✅ 已实现 |
| GET | `/api/v1/chat-sessions/{id}` | 会话详情 | ✅ 已实现 |
| DELETE | `/api/v1/chat-sessions/{id}` | 删除会话（消息物理删除） | ✅ 已实现 |
| GET | `/api/v1/chat-sessions/{id}/messages` | 消息历史（游标分页 `?cursor=&limit=`） | ✅ 已实现 |
| POST | `/api/v1/chat-sessions/{id}/stop` | 打断当前生成 | ⬜ 待实现 |

创建与发消息是分开的：创建走普通 JSON（客户端立刻拿到 id，可用于跳转 URL 或立即出现在列表里），**流式只发生在发消息上**。标题可在创建时指定；未指定时由首条用户消息派生（仅在 `title IS NULL` 时写入，不会覆盖已有标题）。

请求体与查询参数：

```
POST /api/v1/chat-sessions                 { "agentId": 1, "title": "可选" }
POST /api/v1/chat-sessions/{id}/messages   { "content": "..." }   content ≤ 32000

GET /api/v1/chat-sessions?page=1&pageSize=20&agentId=        # agentId 可选过滤
GET /api/v1/chat-sessions/{id}/messages?cursor=&limit=50     # cursor 可选，取更老的一页
```

### 3.2 SSE 事件

用 SSE 协议原生的 `event:` 字段区分类型。四种事件，`start` 每个流一次且最先，`done` / `error` 二选一收尾：

```
event: start
data: {"sessionId":2,"messageId":12}

event: delta
data: {"content":"你"}

event: done
data: {"messageId":12,"finishReason":"stop","promptTokens":15,"completionTokens":13}

event: error
data: {"code":1999,"message":"模型调用失败: TIMEOUT"}
```

**`start` 事件的作用**：新会话的 id 在服务端生成，客户端只能从这里拿到。放在流首而非 `done` 里，是因为流以 `error` 结束时客户端同样需要这个 id——否则会话列表里会多出一条无法续接的孤儿会话。（未来接入 MCP 工具时增加 `tool_call` 事件。）

### 3.3 契约要点

1. **HTTP 状态码只有一次机会**：SSE 一旦开始发送即为 200，之后所有错误（LLM 超时、限流、熔断）只能通过 `event: error` 传递
2. **终止必须显式**：`done` 或 `error` 二选一，客户端据此区分"正常结束"与"连接断了"
3. **客户端消费方式**：`fetch` + `ReadableStream` 手动解析（原生，无新依赖）。不用 `EventSource`——它只支持 GET，无法携带请求体
4. **流开始前的错误仍是普通 HTTP 错误**（agent 禁用、模型禁用、参数校验失败），响应体是标准 `Result<>` JSON
5. **打断**（待实现）：`POST .../stop` 置 Redis 取消标志，流式线程下一轮检查时停止推送，发 `done` 且 `finishReason: "cancelled"`

---

## 4. 实现说明

### 4.1 线程模型

| 阶段 | 线程 |
|---|---|
| 校验 agent → 解析会话 → 写用户消息 → 写占位 → 同步窗口 → 装载窗口 → 提交任务 | Tomcat 请求线程（`http-nio-8080-exec-N`） |
| 调用 LLM、收 delta、推送 SSE、收尾落库 | llmExecutor 线程（`llm-N`），唯一一次切换 |
| `onTimeout` / `onError` 回调 | 容器线程（Tomcat 异步超时/Poller） |

回调线程只置 `cancelled` 标志，**不碰数据库**——消息行由流式线程独占，避免竞态。

### 4.2 事务

流式方法**没有也不会有** `@Transactional`：SSE 连接最长挂 60s，一个横跨它的事务会钉住 Hikari 池（10 个连接）中的一条。所有写入走 `ChatPersistenceService` 的短事务方法。

**必须拆成独立 bean**：同一个类里的私有方法即使加注解也不会开启事务（Spring AOP 只代理跨 bean 调用）。

### 4.3 三条异常路径

| 场景 | 处理 |
|---|---|
| **emitter 超时** | `onTimeout` 置 `cancelled` → 流式线程下个 delta 抛 `StreamCancelledException` 中断上游；若它阻塞在读取中，provider 自身 HTTP 超时收尾 |
| **客户端断开** | `onDelta` 中 `send` 抛 IOException → 包成 `ClientDisconnectedException` 抛出 → 穿透 adapter 读取循环 → `LlmHttpClient.stream` 的 try-with-resources 关闭 HTTP 连接（**取消上游，停止计费**）→ 消息标 FAILED 并保留已产出文本 |
| **send 失败** | 先尝试发 `event: error` + `complete()`；连 error 事件都发不出去时才 `completeWithError(e)` 兜底 |

**刻意不用 `completeWithError` 报错**：响应已 commit 成 `200 + text/event-stream`，Spring 会把异常重新派发给 `@ExceptionHandler`，把 JSON 错误体追加进活的 SSE 流，客户端只会看到解析不了的脏数据。

### 4.4 缓存同步细节

- 写用户消息后**立即 append 到窗口缓存**再 `loadWindow`：否则缓存命中时会漏掉刚写入的当前问题
- `loadWindow` 命中缓存时也执行裁剪并写回：`append` 只追加不裁剪，不回收会让缓存随对话轮次无限增长
- `append` 时把空 tool 列表规范化为 `null`：适配器在模型直接回答时返回 `List.of()`，缓存它会写入 Jackson 内部类型名 `ImmutableCollections$ListN`

### 4.5 其他实现约束

- `ChatServiceImpl` 手写构造函数而非 `@RequiredArgsConstructor`：Lombok 仅在配置 `lombok.copyableAnnotations` 时才把 `@Qualifier` 复制到构造参数，而 `llmExecutor` / `asyncExecutor` 两个同类型 bean 会造成注入歧义
- 失败路径**不 append 到 Redis**：失败的回复不进入下一轮上下文；DB 里 FAILED 的消息在窗口重建时也会被 `status` 过滤掉

---

## 5. 已知风险与边界

1. **llmExecutor 容量 = 并发对话上限**。SSE 期间一个活跃对话占用一个线程直到流结束（最长 60s），当前配置 core 10 / max 50 / queue 100 → 同时进行中的对话上限 50。另：`CallerRunsPolicy` 在池满时会让任务跑回 Tomcat 请求线程，而此时 `return emitter` 尚未执行，整个对话会退化为"阻塞 60s 后一次性返回"
2. **超时配置**：`hify.chat.sse-timeout-ms` = 65000（> 60s 业务预算），已外化到 `application.yml`；不配则用 Tomcat 默认 30s
3. **残留 GENERATING**：服务重启会留下"生成中"的孤儿消息，需要启动时批量置 FAILED（待实现）
4. **新建会话的校验边界**：`resolveSession`（建会话）跑在模型/provider 校验之前，模型恰好被禁用时会留下一条空会话
5. **失败轮的上下文形态**：用户直接重发时，窗口里会出现两条连续 user 消息（失败轮的 + 新的）。协议允许，但做"重新生成"时应改用 `parent_id` 挂接
6. **心跳**：Nginx `proxy_read_timeout` 默认 60s，长思考（几十秒无 delta）可能被掐。优先调大 `proxy_read_timeout`；若加心跳需注意 `send()` 非线程安全（心跳线程与业务线程需互斥）
7. **Nginx**：`proxy_buffering off`；应用侧加 `X-Accel-Buffering: no` 更保险
8. **HTTP 错误也是 200**：`GlobalExceptionHandler` 返回裸 `Result<>`（既有约定，全模块一致），客户端靠 `code` 字段判断

---

## 6. 待办

**已实现**
- [x] `schema.sql` 两张表（DROP 重建）
- [x] Entity（`ChatSession` 继承 BaseEntity、`ChatMessage` 独立实体）+ Mapper
- [x] `ChatPersistenceService`（事务拆分）、`ChatContextService`（Redis 窗口）
- [x] `ChatService`：创建会话 + 会话管理（列表/详情/历史/删除）+ 流式发消息（`start`/`delta`/`done`/`error` 事件）
- [x] `hify.chat.*` 配置外化

**待实现**
- [ ] `stop` 打断（取消标志走 Redis，跨实例可见）
- [ ] 启动时清理残留 `GENERATING` 消息
- [ ] token 预算兜底：`max_context_turns` 只数轮数，单条超长消息仍可能撑爆窗口，需在构造窗口时按模型 `context_size` 的 60% 继续丢弃最老的轮次
- [ ] 前端对接（`api/chat.ts` + SSE 解析）
- [ ] `docs/er-diagram.md`（CLAUDE.md 引用但不存在）
