package com.hify.chat.service;

import com.hify.chat.dto.MessageResp;
import com.hify.chat.dto.SessionResp;
import com.hify.common.dto.PageResult;
import java.util.List;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

/** Business facade of the chat engine: streaming replies + session management. */
public interface ChatService {

  // ------------------------------------------------------------------
  // Streaming (SSE). Events: start, delta*, then exactly one of done/error.
  // ------------------------------------------------------------------

  /**
   * Create an empty session for an agent. Deliberately NOT streaming: the
   * client gets the session id back immediately (to navigate to it or show it
   * in the list) and sends messages through
   * {@link #sendMessage(Long, String)} afterwards.
   *
   * @param agentId agent the new session talks to; must exist and be enabled
   * @param title   optional display name; when null it is derived from the
   *                first user message once one arrives
   */
  SessionResp createSession(Long agentId, String title);

  /**
   * Send a user message to an existing session and stream the reply.
   *
   * @param sessionId session to continue; must exist
   * @param content   user message, never blank
   */
  SseEmitter sendMessage(Long sessionId, String content);

  // ------------------------------------------------------------------
  // Session management
  // ------------------------------------------------------------------

  /**
   * Page through sessions, most recently active first. Sessions that never
   * received a message (lastMessageAt NULL) sort last.
   *
   * @param agentId optional filter; null lists every agent's sessions
   */
  PageResult<SessionResp> listSessions(int page, int pageSize, Long agentId);

  /** Detail of one session. */
  SessionResp getSession(Long id);

  /**
   * Message history of a session, oldest first — the order chat UIs render in.
   *
   * <p>Cursor-paginated on the message id, never COUNT(*): the table is the
   * fastest growing one in the schema. Pass the id of the oldest message you
   * already hold to fetch the page before it; pass null for the newest page.
   * A page shorter than {@code limit} means the history is exhausted.
   *
   * @param limit page size, clamped to [1, 100]
   */
  List<MessageResp> listMessages(Long sessionId, Long cursor, int limit);

  /** Delete a session and its messages (messages are removed physically). */
  void deleteSession(Long id);
}
