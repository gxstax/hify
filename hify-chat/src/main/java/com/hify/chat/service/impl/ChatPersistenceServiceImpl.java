package com.hify.chat.service.impl;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.hify.chat.constant.ChatConstants;
import com.hify.chat.entity.ChatMessage;
import com.hify.chat.entity.ChatSession;
import com.hify.chat.mapper.ChatMessageMapper;
import com.hify.chat.mapper.ChatSessionMapper;
import com.hify.chat.service.ChatPersistenceService;
import com.hify.common.exception.BizException;
import com.hify.common.exception.ErrorCode;
import java.time.LocalDateTime;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Short transactional units for session/message writes. See the interface for
 * why these live outside the streaming service.
 */
@Service
@RequiredArgsConstructor
public class ChatPersistenceServiceImpl implements ChatPersistenceService {

  private final ChatSessionMapper chatSessionMapper;
  private final ChatMessageMapper chatMessageMapper;

  @Override
  @Transactional
  public ChatSession createSession(Long agentId, String title) {
    ChatSession session = new ChatSession();
    session.setAgentId(agentId);
    // A caller-supplied title gets the same shape as one derived from the
    // first message; when none is given the title stays null until that
    // first message arrives (see saveUserMessage -> applyTitleIfAbsent).
    session.setTitle(normalizeTitle(title));
    session.setStatus(ChatConstants.SESSION_STATUS_ACTIVE);
    session.setMessageCount(0);
    chatSessionMapper.insert(session);
    return session;
  }

  @Override
  public ChatSession requireSession(Long sessionId) {
    ChatSession session = chatSessionMapper.selectById(sessionId);
    if (session == null) {
      throw new BizException(ErrorCode.NOT_FOUND, "会话不存在: " + sessionId);
    }
    return session;
  }

  @Override
  @Transactional
  public ChatMessage saveUserMessage(Long sessionId, String content) {
    ChatMessage message = new ChatMessage();
    message.setSessionId(sessionId);
    message.setParentId(lastMessageId(sessionId));
    message.setRole(ChatConstants.ROLE_USER);
    message.setContent(content);
    message.setStatus(ChatConstants.MSG_STATUS_COMPLETED);
    message.setPromptTokens(0);
    message.setCompletionTokens(0);
    chatMessageMapper.insert(message);
    touchSession(sessionId);
    applyTitleIfAbsent(sessionId, content);
    updatePreview(sessionId, content);
    return message;
  }

  @Override
  @Transactional
  public ChatMessage createAssistantPlaceholder(Long sessionId, Long parentId) {
    ChatMessage message = new ChatMessage();
    message.setSessionId(sessionId);
    message.setParentId(parentId);
    message.setRole(ChatConstants.ROLE_ASSISTANT);
    message.setContent("");
    message.setStatus(ChatConstants.MSG_STATUS_GENERATING);
    message.setPromptTokens(0);
    message.setCompletionTokens(0);
    chatMessageMapper.insert(message);
    touchSession(sessionId);
    return message;
  }

  @Override
  @Transactional
  public void finishAssistantMessage(Long sessionId, Long messageId, String content,
      String finishReason, Integer promptTokens, Integer completionTokens) {
    chatMessageMapper.update(null, Wrappers.<ChatMessage>lambdaUpdate()
        .eq(ChatMessage::getId, messageId)
        .eq(ChatMessage::getStatus, ChatConstants.MSG_STATUS_GENERATING)
        .set(ChatMessage::getContent, content == null ? "" : content)
        .set(ChatMessage::getStatus, ChatConstants.MSG_STATUS_COMPLETED)
        .set(ChatMessage::getFinishReason, finishReason)
        .set(ChatMessage::getPromptTokens, promptTokens == null ? 0 : promptTokens)
        .set(ChatMessage::getCompletionTokens, completionTokens == null ? 0 : completionTokens)
        .set(ChatMessage::getUpdatedAt, LocalDateTime.now()));
    updatePreview(sessionId, content);
  }

  @Override
  @Transactional
  public void failAssistantMessage(Long sessionId, Long messageId, String partialContent) {
    chatMessageMapper.update(null, Wrappers.<ChatMessage>lambdaUpdate()
        .eq(ChatMessage::getId, messageId)
        .eq(ChatMessage::getStatus, ChatConstants.MSG_STATUS_GENERATING)
        .set(ChatMessage::getContent, partialContent == null ? "" : partialContent)
        .set(ChatMessage::getStatus, ChatConstants.MSG_STATUS_FAILED)
        .set(ChatMessage::getUpdatedAt, LocalDateTime.now()));
    updatePreview(sessionId, partialContent);
  }

  @Override
  @Transactional
  public void deleteSession(Long sessionId) {
    ChatSession session = chatSessionMapper.selectById(sessionId);
    if (session == null) {
      throw new BizException(ErrorCode.NOT_FOUND, "会话不存在: " + sessionId);
    }
    // Messages go physically: chat_message has no logical-delete column, so
    // they only ever leave together with their session.
    chatMessageMapper.delete(Wrappers.<ChatMessage>lambdaQuery()
        .eq(ChatMessage::getSessionId, sessionId));
    chatSessionMapper.deleteById(sessionId);
  }

  /**
   * Name a still-untitled session after its first user message. Guarded by
   * {@code title IS NULL} in the statement, so a title the caller supplied (or
   * one written by a concurrent turn) is never overwritten.
   */
  private void applyTitleIfAbsent(Long sessionId, String content) {
    chatSessionMapper.update(null, Wrappers.<ChatSession>lambdaUpdate()
        .eq(ChatSession::getId, sessionId)
        .isNull(ChatSession::getTitle)
        .set(ChatSession::getTitle, normalizeTitle(content)));
  }

  /** Id of the newest message in the session, null for a brand-new session. */
  private Long lastMessageId(Long sessionId) {
    ChatMessage last = chatMessageMapper.selectOne(Wrappers.<ChatMessage>lambdaQuery()
        .select(ChatMessage::getId)
        .eq(ChatMessage::getSessionId, sessionId)
        .orderByDesc(ChatMessage::getId)
        .last("LIMIT 1"));
    return last == null ? null : last.getId();
  }

  /** message_count + 1 and last_message_at = now, in the caller's transaction. */
  private void touchSession(Long sessionId) {
    chatSessionMapper.update(null, Wrappers.<ChatSession>lambdaUpdate()
        .eq(ChatSession::getId, sessionId)
        .setSql("message_count = message_count + 1")
        .set(ChatSession::getLastMessageAt, LocalDateTime.now())
        .set(ChatSession::getUpdatedAt, LocalDateTime.now()));
  }

  /**
   * Reduce a title source to what the column should hold: first line only,
   * trimmed, at most {@link ChatConstants#TITLE_MAX_LENGTH} characters.
   *
   * <p>Applied to BOTH user-supplied titles and ones derived from the first
   * message, so the two paths cannot diverge — without this a caller could
   * store a 128-char title while the console expects the 30-char shape.
   */
  private String normalizeTitle(String raw) {
    return singleLine(raw, ChatConstants.TITLE_MAX_LENGTH);
  }

  /** First line only, trimmed, capped at {@code maxLength} characters. */
  private String singleLine(String raw, int maxLength) {
    if (raw == null || raw.isBlank()) {
      return null;
    }
    String oneLine = raw.strip().split("\\R", 2)[0].strip();
    return oneLine.length() <= maxLength ? oneLine : oneLine.substring(0, maxLength);
  }

  /**
   * Refresh the session-list excerpt. Blank content leaves the previous
   * excerpt alone rather than blanking the card (e.g. a failed turn that
   * produced no text).
   */
  private void updatePreview(Long sessionId, String content) {
    String preview = singleLine(content, ChatConstants.PREVIEW_MAX_LENGTH);
    if (preview == null) {
      return;
    }
    chatSessionMapper.update(null, Wrappers.<ChatSession>lambdaUpdate()
        .eq(ChatSession::getId, sessionId)
        .set(ChatSession::getLastMessagePreview, preview));
  }
}
