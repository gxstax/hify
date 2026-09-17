# Chat 对话引擎 — 步骤 1 决策记录

> 状态：待确认（确认后进入步骤 2：schema.sql 落地）
> 模块：`hify-chat`（当前为空壳，仅 package-info 占位）

## 范围

对话引擎负责：会话管理、流式对话（SSE）、上下文窗口管理。依赖 `hify-agent`（Agent 配置）与 `hify-provider`（适配层已定型的 `ChatRequest` / `ChatResponse` / `ChatStreamCallback`）。

不在本期范围：RAG 检索接入、工作流触发、消息反馈（点赞/点踩）、多模态内容。

---

## 1. 数据模型

### 1.1 表清单：2 张

`chat_session` + `chat_message`。工具调用、RAG 引用等放 `chat_message.meta`，不建独立表。

### 1.2 DDL 草案

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
  finish_reason     VARCHAR(32) NULL COMMENT 'stop | length | tool_calls | ...',
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

### 1.3 相对现有 schema.sql 的变更

现有 `chat_session` / `chat_message` 是草案，表内无数据，直接 DROP 重建。

| 表 | 变更 | 理由 |
|---|---|---|
| chat_session | +`last_message_at` +`message_count` | 会话列表按最近活跃排序 + 展示条数，避免聚合大表 |
| chat_session | 保留 `deleted` | 会话是用户可见的删除对象，软删合理 |
| chat_message | +`parent_id` | 重新生成/分支预留，表无数据时零成本 |
| chat_message | +`status` +`finish_reason` | 流式先占位后更新的状态机（见 1.4） |
| chat_message | `tokens` → 拆 `prompt_tokens`/`completion_tokens` | `ChatResponse` 已分开返回，成本统计需要 |
| chat_message | **去掉 `deleted`** | 增长最快的表：查询带 `WHERE deleted=0`、索引多一列；删除走"删会话 → 按 session_id 物理级联删" |
| chat_message | `content` LONGTEXT 注释修正 | 原注释写 MEDIUMTEXT 与类型不符 |

### 1.4 决策记录（4 条）

| 决策点 | 采纳 | 被否决方案与取舍 |
|---|---|---|
| 流式落库 | **先占位后更新**：INSERT 空消息（GENERATING）→ 流结束 UPDATE（COMPLETED + content + usage）；异常标 FAILED | ① 一次性写入：流中断即丢整条回复，刷新后消息消失；② 周期 UPDATE：大表写放大 |
| chat_message 逻辑删除 | **去掉 `deleted`** | 保留：所有查询带 `WHERE deleted=0`，索引变 `(session_id, deleted, id)`。先例：`provider_health` 同样无逻辑删除 |
| 工具调用存储 | **放 `meta` JSON** | 独立 `chat_tool_call` 表：MVP 用不上，多一张表多一次写入；工具调用统计属 MCP 模块的后续议题 |
| 重新生成 | **加 `parent_id` 预留** | 不加：以后补字段需迁移已有数据。语义见下 |

**`parent_id` 语义**：指向它所回复的那条消息。user 消息 → 上一轮 assistant（首条为 NULL）；assistant 消息 → 触发它的 user。重新生成时同一 `parent_id` 下有多条 assistant，`id` 最大者为当前分支。

### 1.5 存储与上下文策略

- **会话 / 消息**：MySQL 全量持久化，不缓存（按 CLAUDE.md）
- **上下文窗口**：Redis `session:{sessionId}`，TTL 2h，存最近 `agent.max_context_turns` 轮的轻量消息（role / content / toolCalls）
  - 每轮结束后 append + 截断；缓存 miss 时从 MySQL 按 `(session_id, id)` 倒序回填
  - **截断必须按完整轮次边界切**：否则 assistant.tool_calls + tool 结果这对消息被切一半，回传 OpenAI 协议直接报错
- **分页**：会话列表普通分页（按 `last_message_at DESC`，NULL 排最后）；消息列表**游标分页**（`id < cursor` 倒序取 N 条），不做 COUNT(*)
- **计数维护**：`message_count` 在消息 INSERT 时 +1（user 进来 +1、assistant 占位 +1），中断也能对上

### 1.6 实体层注意

`chat_message` 无 `deleted` → **不能继承 `BaseEntity`**（`@TableLogic` 会生成 `WHERE deleted=0` 直接报错）。照 `ProviderHealth` 的写法用独立实体，`created_at`/`updated_at` 仍用 `@TableField(fill = ...)`。

**不加 `user_id`**：按 CLAUDE.md"不做多租户/权限体系"，全部会话共享。若以后要按人隔离，是改表 + 改全部查询。

---

## 2. 传输层：SSE（而非 WebSocket）

理由：对话是"发一条 → 流式收一条"的请求-响应模型，单向推送足够；鉴权/代理/网关全部复用 HTTP；WebSocket 的全双工能力在此场景全无用武之地，却要付出连接注册表、心跳、重连、帧协议设计的成本。行业标准亦如此（OpenAI / Anthropic 流式 API、Dify 均为 SSE）。CLAUDE.md 已定调。

被否决：WebSocket（双向能力过剩、浏览器 WS API 无法带自定义 header 鉴权、Nginx/K8s Ingress 需额外配置）。将来若出现语音对话/多人协同等真双向需求，再加 WS 通道，不影响现有契约。

---

## 3. 接口契约

路径风格沿用现有约定（资源复数名、连字符，无模块前缀）：`/api/v1/chat-sessions`。

| 方法 | 路径 | 说明 |
|---|---|---|
| POST | `/api/v1/chat-sessions` | 创建会话 `{agentId, title?}` |
| GET | `/api/v1/chat-sessions` | 会话列表（分页，`last_message_at DESC`） |
| GET | `/api/v1/chat-sessions/{id}` | 会话详情 |
| DELETE | `/api/v1/chat-sessions/{id}` | 删除会话（级联物理删消息，前端二次确认） |
| POST | `/api/v1/chat-sessions/{id}/messages` | 发消息，**SSE 流式响应** |
| GET | `/api/v1/chat-sessions/{id}/messages` | 消息列表（游标分页 `?cursor=&limit=`） |
| POST | `/api/v1/chat-sessions/{id}/stop` | 打断当前生成 |

### SSE 事件格式

用 SSE 协议原生的 `event:` 字段区分事件类型（而非 data 内嵌 type）：

```
event: delta
data: {"content":"你"}

event: tool_call
data: {"name":"weather","arguments":"{...}"}

event: done
data: {"messageId":123,"finishReason":"stop","promptTokens":45,"completionTokens":12}

event: error
data: {"code":4001,"message":"..."}
```

### 契约要点

1. **HTTP 状态码只有一次机会**：SSE 一旦开始发送即为 200，之后所有错误（LLM 超时、限流、熔断）只能通过 `event: error` 传递，错误码走 4000-4999 段
2. **终止必须显式**：`done` 或 `error` 二选一，前端据此区分"正常结束"与"断了"
3. **客户端消费方式**：`fetch` + `ReadableStream` 手动解析（原生，无新依赖）。不用 `EventSource`——它只支持 GET，无法携带消息 body
4. **打断**：`POST .../stop` 置取消标志，流式线程下一轮检查时停止推送并收尾（发 `done`，`finishReason: "cancelled"`）

---

## 4. 实现骨架（参考）

```java
@PostMapping(value = "/{id}/messages", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
public SseEmitter send(@PathVariable Long id, @RequestBody @Valid MessageReq req) {
  SseEmitter emitter = new SseEmitter(sseTimeoutMs);   // 外化到 yml，见 §5.2
  llmExecutor.execute(() -> {                          // 唯一一次线程切换
    try {
      chatService.streamReply(id, req, new ChatStreamCallback() {
        public void onDelta(String content) {
          try {
            emitter.send(SseEmitter.event().name("delta").data(deltaEvent(content)));
          } catch (IOException e) {
            // Client gone: abort the upstream LLM call by propagating.
            throw new ClientDisconnectedException(e);
          }
        }
        public void onComplete(ChatResponse resp) {
          try {
            emitter.send(SseEmitter.event().name("done").data(doneEvent(resp)));
          } catch (IOException e) {
            // Client gone; nothing to report.
          }
          emitter.complete();
        }
      });
    } catch (Exception e) {
      log.warn("stream failed, sessionId={}", id, e);
      try {
        emitter.send(SseEmitter.event().name("error").data(errorEvent(e)));
      } catch (IOException ignored) {
        // Client already gone.
      }
      emitter.complete();                              // NOT completeWithError, see §5.3
    }
  });
  return emitter;
}
```

三处刻意的写法，都是踩过的坑：

1. **`onDelta` 里 catch IOException 后重新抛出**（而非吞掉）—— 这是唯一能中断上游 LLM 请求的信号。吞掉 = 客户端已断开但 LLM 继续跑完 = 白烧 token。异常会穿透适配器读取循环，触发其关闭 HTTP 连接
2. **异常路径发 `event: error` 再 `complete()`**，不用 `completeWithError()` —— 后者会把异常重新派发给 `@ExceptionHandler`，而响应已 commit（200 + `text/event-stream`），错误 JSON 会被追加进 SSE 流，前端解析不了，只能靠日志发现
3. **`streamReply` 内部**：`catch` + `finally` 保证消息状态被终结为 `FAILED`，且**异常时把已累积的 delta 落库**（否则前端显示了半截回复、库里却是空的 GENERATING 记录）

---

## 5. 已知风险与边界

1. **llmExecutor 容量 = 并发对话上限**。SSE 期间一个活跃对话占用一个线程直到流结束（可长达 60s），当前配置 core 10 / max 50 / queue 100（`ThreadPoolConfig.java:28`）→ 同时进行中的对话上限 50。另：`CallerRunsPolicy` 在池满时会让任务跑回 Tomcat 请求线程，而此时 `return emitter` 尚未执行，整个对话退化为"阻塞 60s 后一次性返回"。50 人规模基本碰不到，压测时验证
2. **超时必须显式配置**。`application.yml` 未设 `spring.mvc.async.request-timeout` → 用 Tomcat 默认 **30 秒**，对话会被提前掐断。需显式设置 `SseEmitter` 超时（60s+，留余量让业务超时先生效），配置外化
3. **残留 GENERATING**：服务重启会留下"生成中"的孤儿消息，启动时批量置 `FAILED`
4. **心跳**：Nginx `proxy_read_timeout` 默认 60s，长思考（几十秒无 delta）可能被掐。优先调大 `proxy_read_timeout`；若加心跳则注意 `send()` 非线程安全（心跳线程与业务线程需互斥，或统一单线程发送）
5. **Nginx**：`proxy_buffering off`（CLAUDE.md 已记）；应用侧加 `X-Accel-Buffering: no` 更保险
6. **`send()` 非线程安全**：本设计中发送全部在 llmExecutor 单线程内顺序发生，天然安全；引入任何第二发送方（心跳、超时回调里的 send）都会破坏这一点

---

## 6. 步骤 2 同步清单

- [ ] `schema.sql`：DROP 重建 `chat_session` / `chat_message`（表内无数据）
- [ ] 补建 `docs/er-diagram.md`（CLAUDE.md 引用但不存在）
- [ ] Entity：`ChatSession`（继承 BaseEntity）、`ChatMessage`（独立实体，无 deleted）
- [ ] 前端类型：`hify-web/src/types` 与 `api/chat.ts`（步骤 5 建，此处仅登记）
- [ ] 配置项：`hify.chat.sse-timeout-ms` 等外化到 `application.yml`
