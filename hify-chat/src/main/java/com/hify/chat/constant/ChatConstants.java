package com.hify.chat.constant;

/** Chat-module constants: roles, message/session states, Redis keys. */
public final class ChatConstants {

  private ChatConstants() {
  }

  // ---- message roles (mirror com.hify.provider.adapter.ChatMessage) ----
  public static final String ROLE_USER = "user";
  public static final String ROLE_ASSISTANT = "assistant";

  // ---- message status ----
  /** Assistant placeholder written before the stream starts. */
  public static final String MSG_STATUS_GENERATING = "GENERATING";
  public static final String MSG_STATUS_COMPLETED = "COMPLETED";
  /** Stream aborted (provider error, timeout, client disconnect). */
  public static final String MSG_STATUS_FAILED = "FAILED";

  // ---- session status ----
  public static final String SESSION_STATUS_ACTIVE = "ACTIVE";
  public static final String SESSION_STATUS_ARCHIVED = "ARCHIVED";

  /** Context window cache: session:{sessionId}, TTL from hify.chat.context-ttl. */
  public static final String CONTEXT_KEY_PREFIX = "session:";

  /** Applied when neither the request nor the agent specifies a turn limit. */
  public static final int DEFAULT_MAX_CONTEXT_TURNS = 10;

  /** Session titles are derived from the first user message, truncated here. */
  public static final int TITLE_MAX_LENGTH = 30;

  /** Session-list excerpt length; the UI truncates further with CSS. */
  public static final int PREVIEW_MAX_LENGTH = 60;
}
