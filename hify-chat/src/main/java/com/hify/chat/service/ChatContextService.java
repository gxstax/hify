package com.hify.chat.service;

import com.hify.provider.adapter.ChatMessage;
import java.util.List;

/**
 * The conversation window sent to the model — the "memory" of a stateless
 * LLM. Backed by Redis ({@code session:{sessionId}}, TTL from
 * {@code hify.chat.context-ttl}) with MySQL as the source of truth: a cache
 * miss rebuilds the window from chat_message, so losing Redis costs one query,
 * never data.
 *
 * <p>Messages here are {@link ChatMessage} (the provider-neutral shape the
 * adapters consume), not the DB entity. The system prompt is NOT part of the
 * window — it comes from the agent config on every request, so editing an
 * agent takes effect immediately without touching the cache.
 */
public interface ChatContextService {

  /**
   * Most recent {@code maxTurns} turns, oldest first, ready to append after
   * the system prompt. Rebuilt from MySQL on a cache miss.
   *
   * <p>Truncation always cuts on a full-turn boundary: a window that starts
   * mid-turn (e.g. a tool result without its assistant tool_calls) is rejected
   * by the provider protocol.
   */
  List<ChatMessage> loadWindow(Long sessionId, int maxTurns);

  /**
   * Append finished turns to the cached window and re-trim it. No-op when the
   * session has no cached window — the next {@link #loadWindow} rebuilds from
   * MySQL and picks the messages up anyway.
   */
  void append(Long sessionId, ChatMessage... messages);

  /** Drop the cached window; the next load rebuilds it from MySQL. */
  void evict(Long sessionId);
}
