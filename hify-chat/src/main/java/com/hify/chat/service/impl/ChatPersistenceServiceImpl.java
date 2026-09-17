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
    // No title yet when the caller gave none: it is derived from the first
    // user message (see saveUserMessage).
    session.setTitle(title == null || title.isBlank() ? null : title.strip());
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
  public void finishAssistantMessage(Long messageId, String content, String finishReason,
      Integer promptTokens, Integer completionTokens) {
    chatMessageMapper.update(null, Wrappers.<ChatMessage>lambdaUpdate()
        .eq(ChatMessage::getId, messageId)
        .eq(ChatMessage::getStatus, ChatConstants.MSG_STATUS_GENERATING)
        .set(ChatMessage::getContent, content == null ? "" : content)
        .set(ChatMessage::getStatus, ChatConstants.MSG_STATUS_COMPLETED)
        .set(ChatMessage::getFinishReason, finishReason)
        .set(ChatMessage::getPromptTokens, promptTokens == null ? 0 : promptTokens)
        .set(ChatMessage::getCompletionTokens, completionTokens == null ? 0 : completionTokens)
        .set(ChatMessage::getUpdatedAt, LocalDateTime.now()));
  }

  @Override
  @Transactional
  public void failAssistantMessage(Long messageId, String partialContent) {
    chatMessageMapper.update(null, Wrappers.<ChatMessage>lambdaUpdate()
        .eq(ChatMessage::getId, messageId)
        .eq(ChatMessage::getStatus, ChatConstants.MSG_STATUS_GENERATING)
        .set(ChatMessage::getContent, partialContent == null ? "" : partialContent)
        .set(ChatMessage::getStatus, ChatConstants.MSG_STATUS_FAILED)
        .set(ChatMessage::getUpdatedAt, LocalDateTime.now()));
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
        .set(ChatSession::getTitle, deriveTitle(content)));
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

  /** First line of the first user message, truncated (CLAUDE.md: title <= 128). */
  private String deriveTitle(String firstUserContent) {
    if (firstUserContent == null || firstUserContent.isBlank()) {
      return null;
    }
    String oneLine = firstUserContent.strip().split("\\R", 2)[0].strip();
    return oneLine.length() <= ChatConstants.TITLE_MAX_LENGTH
        ? oneLine
        : oneLine.substring(0, ChatConstants.TITLE_MAX_LENGTH);
  }
}
