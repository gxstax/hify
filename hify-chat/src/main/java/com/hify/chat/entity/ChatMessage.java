package com.hify.chat.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.extension.handlers.JacksonTypeHandler;
import java.time.LocalDateTime;
import java.util.Map;
import lombok.Getter;
import lombok.Setter;

/**
 * A single message in a session — the fastest growing table in the schema.
 *
 * <p>Deliberately NOT extending {@link com.hify.common.entity.BaseEntity}: the
 * table has no logical-delete column. Every read of this table would otherwise
 * carry {@code WHERE deleted = 0} and the (session_id, id) index would widen.
 * Rows are physically removed together with their session; precedent:
 * {@link com.hify.provider.entity.ProviderHealth}.
 *
 * <p>{@code status} state machine (streaming writes a placeholder first):
 * assistant rows start as GENERATING, then become COMPLETED (content + usage
 * filled) or FAILED (content keeps the deltas already produced).
 */
@Getter
@Setter
@TableName(value = "chat_message", autoResultMap = true)
public class ChatMessage {

  @TableId(type = IdType.AUTO)
  private Long id;

  /** chat_session.id (app-layer FK). */
  private Long sessionId;

  /** Message being replied to; NULL for the first message of a session. */
  private Long parentId;

  /** user | assistant | system | tool. */
  private String role;

  /** Message text. */
  private String content;

  /** GENERATING / COMPLETED / FAILED. */
  private String status;

  /** Provider stop reason: stop | length | tool_calls | cancelled | ... */
  private String finishReason;

  /** Prompt tokens reported by the provider (0 when unreported). */
  private Integer promptTokens;

  /** Completion tokens reported by the provider (0 when unreported). */
  private Integer completionTokens;

  /** Extras: tool_calls / tool_call_id / RAG citations.
   *  Needs both the type handler and {@code autoResultMap = true} on the
   *  class, otherwise inserts work but selects silently return null. */
  @TableField(typeHandler = JacksonTypeHandler.class)
  private Map<String, Object> meta;

  @TableField(fill = FieldFill.INSERT)
  private LocalDateTime createdAt;

  @TableField(fill = FieldFill.INSERT_UPDATE)
  private LocalDateTime updatedAt;
}
