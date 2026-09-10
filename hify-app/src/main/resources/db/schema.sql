-- hify database schema (MySQL 8.x)
--
-- Conventions per CLAUDE.md:
--   names lower_snake_case; PK `id` bigint auto-increment; created_at/updated_at
--   datetime (auto-filled by the application, DB default as a fallback);
--   logical delete `deleted` tinyint (0 = normal, 1 = deleted);
--   charset utf8mb4; NO database-level foreign keys (maintained in the app layer).
--
-- Apply manually (assumes database `hify` exists; run the two commented lines once if not):
--   CREATE DATABASE IF NOT EXISTS hify DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
--   USE hify;
--   mysql -u root -p < hify-app/src/main/resources/db/schema.sql
--
-- The file is idempotent (IF NOT EXISTS); after altering a table manually,
-- DROP it first or it will silently keep the old definition.

-- ---------------------------------------------------------------------------
-- provider: a model-provider access instance (one protocol + one credential set)
-- auth_config structure depends on `type`:
--   OPENAI / OPENAI_COMPATIBLE -> { "apiKey": "sk-..." }   (Bearer)
--   ANTHROPIC                 -> { "apiKey": "sk-ant-..." } (x-api-key header)
--   OLLAMA                    -> null (no auth)
-- Future types may carry { clientId, clientSecret, tokenUrl, ... } etc.
-- ---------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS provider (
  id         BIGINT        NOT NULL AUTO_INCREMENT COMMENT 'Primary key',
  name       VARCHAR(100)  NOT NULL COMMENT '供应商名称，唯一',
  type       VARCHAR(30)   NOT NULL COMMENT 'OPENAI / ANTHROPIC / DEEPSEEK / OLLAMA / OPENAI_COMPATIBLE',
  base_url   VARCHAR(500)  NOT NULL COMMENT 'API 基础地址',
  auth_config JSON         NULL COMMENT '鉴权配置，结构按 type 不同',
  description VARCHAR(255) NULL COMMENT '备注',
  enabled    TINYINT       NOT NULL DEFAULT 1 COMMENT '0=禁用 1=启用',
  created_at DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT 'Creation time',
  updated_at DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT 'Last update time',
  deleted    TINYINT       NOT NULL DEFAULT 0 COMMENT 'Logical delete: 0 = normal, 1 = deleted',
  PRIMARY KEY (id),
  UNIQUE KEY uk_provider_name (name)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_unicode_ci
  COMMENT = '模型提供商';

-- ---------------------------------------------------------------------------
-- model_config: models exposed by one provider access instance (N:1)
-- ---------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS model_config (
  id           BIGINT       NOT NULL AUTO_INCREMENT COMMENT 'Primary key',
  provider_id  BIGINT       NOT NULL COMMENT '所属供应商 ID，provider.id（应用层外键）',
  name         VARCHAR(100) NOT NULL COMMENT '展示名，如 GPT-4o',
  model_id     VARCHAR(100) NOT NULL COMMENT '调用时传给 API 的值',
  context_size INT          NULL COMMENT '上下文窗口大小（token 数）',
  extra_params JSON         NULL COMMENT '模型级别扩展参数',
  enabled      TINYINT      NOT NULL DEFAULT 1 COMMENT '0=禁用 1=启用',
  created_at   DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT 'Creation time',
  updated_at   DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT 'Last update time',
  deleted      TINYINT      NOT NULL DEFAULT 0 COMMENT 'Logical delete: 0 = normal, 1 = deleted',
  PRIMARY KEY (id),
  KEY idx_model_config_provider_id (provider_id)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_unicode_ci
  COMMENT = '模型配置';

-- ---------------------------------------------------------------------------
-- provider_health: 1:1 probe results per provider. High write frequency, kept
-- out of the provider row so it never invalidates the provider cache.
-- No logical delete: one row per provider, upsert semantics.
-- ---------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS provider_health (
  id             BIGINT       NOT NULL AUTO_INCREMENT COMMENT 'Primary key',
  provider_id    BIGINT       NOT NULL COMMENT '供应商 ID，唯一',
  status         VARCHAR(20)  NOT NULL DEFAULT 'UNKNOWN' COMMENT 'UP / DOWN / DEGRADED / UNKNOWN',
  last_check_at  DATETIME     NULL COMMENT '最后探测时间',
  last_success_at DATETIME    NULL COMMENT '最后成功时间',
  fail_count     INT          NOT NULL DEFAULT 0 COMMENT '连续失败次数',
  latency_ms     INT          NULL COMMENT '最近一次延迟 ms',
  error_message  VARCHAR(500) NULL COMMENT '最近失败原因',
  updated_at     DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT 'Last update time',
  PRIMARY KEY (id),
  UNIQUE INDEX idx_provider_health_provider_id (provider_id)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_unicode_ci
  COMMENT = '供应商健康状态';

-- ---------------------------------------------------------------------------
-- agent: agent definition (model binding + system prompt; MCP tools via agent_tool)
-- ---------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS agent (
  id              BIGINT        NOT NULL AUTO_INCREMENT COMMENT 'Primary key',
  name            VARCHAR(128)  NOT NULL COMMENT 'Agent name shown in the console',
  description     VARCHAR(512)  NULL COMMENT 'Short description',
  system_prompt   TEXT          NULL COMMENT 'System prompt defining agent behavior',
  model_config_id BIGINT        NOT NULL COMMENT 'Bound model, model_config.id (app-layer FK)',
  temperature     DOUBLE        NULL COMMENT 'Sampling temperature; NULL = provider default',
  max_tokens      INT           NULL COMMENT 'Max output tokens; NULL = provider default',
  created_at      DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT 'Creation time',
  updated_at      DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT 'Last update time',
  deleted         TINYINT       NOT NULL DEFAULT 0 COMMENT 'Logical delete: 0 = normal, 1 = deleted',
  PRIMARY KEY (id),
  KEY idx_agent_model_config_id (model_config_id)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_unicode_ci
  COMMENT = 'Agent configurations';

-- ---------------------------------------------------------------------------
-- mcp_server: an MCP server exposing tools callable by agents
-- ---------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS mcp_server (
  id          BIGINT        NOT NULL AUTO_INCREMENT COMMENT 'Primary key',
  name        VARCHAR(128)  NOT NULL COMMENT 'Server name',
  description VARCHAR(255)  NULL COMMENT 'Short description',
  transport   VARCHAR(16)   NOT NULL COMMENT 'Transport type: stdio | sse',
  command     VARCHAR(255)  NULL COMMENT 'For stdio: command to launch',
  args        JSON          NULL COMMENT 'For stdio: launch arguments as a JSON array',
  url         VARCHAR(512)  NULL COMMENT 'For sse: MCP endpoint URL',
  created_at  DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT 'Creation time',
  updated_at  DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT 'Last update time',
  deleted     TINYINT       NOT NULL DEFAULT 0 COMMENT 'Logical delete: 0 = normal, 1 = deleted',
  PRIMARY KEY (id),
  UNIQUE KEY uk_mcp_server_name (name)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_unicode_ci
  COMMENT = 'MCP servers';

-- ---------------------------------------------------------------------------
-- agent_tool: M:N binding between agents and MCP servers
-- ---------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS agent_tool (
  id            BIGINT    NOT NULL AUTO_INCREMENT COMMENT 'Primary key',
  agent_id      BIGINT    NOT NULL COMMENT 'agent.id (app-layer FK)',
  mcp_server_id BIGINT    NOT NULL COMMENT 'mcp_server.id (app-layer FK)',
  created_at    DATETIME  NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT 'Creation time',
  updated_at    DATETIME  NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT 'Last update time',
  deleted       TINYINT   NOT NULL DEFAULT 0 COMMENT 'Logical delete: 0 = normal, 1 = deleted',
  PRIMARY KEY (id),
  UNIQUE KEY uk_agent_tool_agent_server (agent_id, mcp_server_id)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_unicode_ci
  COMMENT = 'Agent to MCP server bindings';

-- ---------------------------------------------------------------------------
-- chat_session: one conversation with an agent
-- ---------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS chat_session (
  id         BIGINT       NOT NULL AUTO_INCREMENT COMMENT 'Primary key',
  agent_id   BIGINT       NOT NULL COMMENT 'Agent this session talks to, agent.id (app-layer FK)',
  title      VARCHAR(128) NULL COMMENT 'Session title, usually derived from the first message',
  created_at DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT 'Creation time',
  updated_at DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT 'Last update time',
  deleted    TINYINT      NOT NULL DEFAULT 0 COMMENT 'Logical delete: 0 = normal, 1 = deleted',
  PRIMARY KEY (id),
  KEY idx_chat_session_agent_id (agent_id)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_unicode_ci
  COMMENT = 'Chat sessions';

-- ---------------------------------------------------------------------------
-- chat_message: a single message in a session (fastest growing table)
-- ---------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS chat_message (
  id         BIGINT      NOT NULL AUTO_INCREMENT COMMENT 'Primary key',
  session_id BIGINT      NOT NULL COMMENT 'Owning session, chat_session.id (app-layer FK)',
  role       VARCHAR(16) NOT NULL COMMENT 'Message role: user | assistant | system | tool',
  content    MEDIUMTEXT  NOT NULL COMMENT 'Message text (MEDIUMTEXT: replies can exceed TEXT limits)',
  meta       JSON        NULL COMMENT 'Extras: tool calls, RAG citations, ...',
  created_at DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT 'Creation time',
  updated_at DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT 'Last update time',
  deleted    TINYINT     NOT NULL DEFAULT 0 COMMENT 'Logical delete: 0 = normal, 1 = deleted',
  PRIMARY KEY (id),
  KEY idx_chat_message_session_id_id (session_id, id)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_unicode_ci
  COMMENT = 'Chat messages';

-- ---------------------------------------------------------------------------
-- demo_item: CRUD scaffold demo (remove once real business entities land)
-- ---------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS demo_item (
  id         BIGINT       NOT NULL AUTO_INCREMENT COMMENT 'Primary key',
  name       VARCHAR(128) NOT NULL COMMENT 'Item name',
  status     INT          NOT NULL COMMENT 'Item status (demo field)',
  created_at DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT 'Creation time',
  updated_at DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT 'Last update time',
  deleted    TINYINT      NOT NULL DEFAULT 0 COMMENT 'Logical delete: 0 = normal, 1 = deleted',
  PRIMARY KEY (id)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_unicode_ci
  COMMENT = 'Demo CRUD entity';
