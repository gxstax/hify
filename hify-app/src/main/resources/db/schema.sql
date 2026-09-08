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
-- provider: model providers (OpenAI / Claude / Gemini / Ollama)
-- ---------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS provider (
  id           BIGINT      NOT NULL AUTO_INCREMENT COMMENT 'Primary key',
  name         VARCHAR(64) NOT NULL COMMENT 'Provider key, e.g. openai / claude / gemini / ollama',
  display_name VARCHAR(64) NOT NULL COMMENT 'Human readable name shown in the console',
  enabled      TINYINT     NOT NULL DEFAULT 1 COMMENT '1 = enabled, 0 = disabled',
  created_at   DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT 'Creation time',
  updated_at   DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT 'Last update time',
  deleted      TINYINT     NOT NULL DEFAULT 0 COMMENT 'Logical delete: 0 = normal, 1 = deleted',
  PRIMARY KEY (id),
  UNIQUE KEY uk_provider_name (name)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_unicode_ci
  COMMENT = 'Model providers';

-- ---------------------------------------------------------------------------
-- model_config: a concrete model access config, bound to one provider (N:1)
-- ---------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS model_config (
  id           BIGINT        NOT NULL AUTO_INCREMENT COMMENT 'Primary key',
  provider_id  BIGINT        NOT NULL COMMENT 'Owning provider, provider.id (app-layer FK)',
  name         VARCHAR(128)  NOT NULL COMMENT 'Model id as the provider API expects, e.g. gpt-4o',
  display_name VARCHAR(128)  NULL COMMENT 'Optional label for the console',
  type         VARCHAR(16)   NOT NULL DEFAULT 'chat' COMMENT 'Model kind: chat | embedding',
  api_key      VARCHAR(512)  NULL COMMENT 'API key for this config; NULL when not needed (e.g. local Ollama)',
  base_url     VARCHAR(512)  NULL COMMENT 'API base URL override; NULL = provider default endpoint',
  enabled      TINYINT       NOT NULL DEFAULT 1 COMMENT '1 = enabled, 0 = disabled',
  created_at   DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT 'Creation time',
  updated_at   DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT 'Last update time',
  deleted      TINYINT       NOT NULL DEFAULT 0 COMMENT 'Logical delete: 0 = normal, 1 = deleted',
  PRIMARY KEY (id),
  UNIQUE KEY uk_model_config_provider_name_type (provider_id, name, type),
  KEY idx_model_config_provider_id (provider_id)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_unicode_ci
  COMMENT = 'Model access configs';

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
