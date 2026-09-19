package com.hify.chat.service;

import com.hify.chat.entity.ChatMessage;
import com.hify.chat.entity.ChatSession;

/**
 * Transactional writes of sessions/messages, split into short units.
 *
 * <p>Why this is a separate bean and not private methods on
 * {@code ChatServiceImpl}: a streaming request holds its SSE connection open
 * for up to a minute, and it must NOT run inside a transaction — one
 * connection from the 10-connection Hikari pool would be pinned for the whole
 * stream. Every method here is therefore a short @Transactional unit called
 * between stream steps. (It also has to be a different bean: Spring's
 * transaction proxy only applies to calls that cross a bean boundary — a
 * self-invocation inside the same class would silently run without a
 * transaction.)
 */
public interface ChatPersistenceService {

  /**
   * Insert an empty session for {@code agentId}.
   *
   * @param title optional; null leaves it to be derived from the first message
   */
  ChatSession createSession(Long agentId, String title);

  /**
   * Load a session by id.
   *
   * @throws com.hify.common.exception.BizException NOT_FOUND when unknown
   */
  ChatSession requireSession(Long sessionId);

  /**
   * Persist the user turn (status COMPLETED) and bump the session counters.
   *
   * @return the inserted row, carrying the generated id
   */
  ChatMessage saveUserMessage(Long sessionId, String content);

  /**
   * Write the assistant placeholder before the stream starts (status
   * GENERATING, empty content). Guarantees a row exists to update or fail
   * even if the client disconnects mid-stream.
   */
  ChatMessage createAssistantPlaceholder(Long sessionId, Long parentId);

  /**
   * Mark the placeholder COMPLETED with the aggregated result, and refresh the
   * session's list excerpt. No-op when the row is no longer GENERATING (e.g.
   * already failed by a timeout handler).
   *
   * @param sessionId needed to keep chat_session.last_message_preview in step
   */
  void finishAssistantMessage(Long sessionId, Long messageId, String content, String finishReason,
      Integer promptTokens, Integer completionTokens);

  /**
   * Mark the placeholder FAILED, keeping whatever deltas were produced before
   * the failure so the user still sees the partial answer. No-op when the row
   * is no longer GENERATING.
   *
   * @param sessionId needed to keep chat_session.last_message_preview in step
   */
  void failAssistantMessage(Long sessionId, Long messageId, String partialContent);

  /**
   * Delete a session: logical delete of the session row plus a physical delete
   * of its messages. chat_message carries no {@code deleted} column on
   * purpose — a hot table should not pay for one — so messages only ever leave
   * together with their session.
   *
   * @throws com.hify.common.exception.BizException NOT_FOUND when unknown
   */
  void deleteSession(Long sessionId);
}
