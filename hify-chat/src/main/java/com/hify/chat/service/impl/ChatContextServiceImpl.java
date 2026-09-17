package com.hify.chat.service.impl;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.hify.chat.config.ChatProperties;
import com.hify.chat.constant.ChatConstants;
import com.hify.chat.entity.ChatMessage;
import com.hify.chat.mapper.ChatMessageMapper;
import com.hify.chat.service.ChatContextService;
import com.hify.common.util.RedisUtil;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * Redis-backed conversation window. See {@link ChatContextService}.
 *
 * <p>The cached value is a {@code List<com.hify.provider.adapter.ChatMessage>};
 * RedisConfig's serializer embeds type info, so it round-trips without manual
 * mapping.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ChatContextServiceImpl implements ChatContextService {

  /**
   * Upper bound on rows fetched when rebuilding: a turn is usually 2 messages,
   * a few more once tool calls are involved. The precise cut happens in
   * {@link #trimToTurns}; this only keeps the query small.
   */
  private static final int ROWS_PER_TURN = 4;
  private static final int REBUILD_SLACK = 10;

  private final ChatMessageMapper chatMessageMapper;
  private final RedisUtil redisUtil;
  private final ChatProperties chatProperties;

  @Override
  public List<com.hify.provider.adapter.ChatMessage> loadWindow(Long sessionId, int maxTurns) {
    int turns = maxTurns > 0 ? maxTurns : ChatConstants.DEFAULT_MAX_CONTEXT_TURNS;
    String key = contextKey(sessionId);

    List<com.hify.provider.adapter.ChatMessage> cached = redisUtil.get(key);
    if (cached != null) {
      // Trim on read: append() adds without trimming, so the cached list may
      // sit slightly above the window size. Writing the trimmed view back
      // keeps it from growing across a long conversation.
      List<com.hify.provider.adapter.ChatMessage> trimmed = trimToTurns(cached, turns);
      if (trimmed.size() != cached.size()) {
        redisUtil.set(key, trimmed, chatProperties.getContextTtl());
      }
      return trimmed;
    }

    List<com.hify.provider.adapter.ChatMessage> rebuilt =
        trimToTurns(rebuildFromDb(sessionId, turns), turns);
    redisUtil.set(key, rebuilt, chatProperties.getContextTtl());
    return rebuilt;
  }

  @Override
  public void append(Long sessionId, com.hify.provider.adapter.ChatMessage... messages) {
    if (messages == null || messages.length == 0) {
      return;
    }
    String key = contextKey(sessionId);
    List<com.hify.provider.adapter.ChatMessage> cached = redisUtil.get(key);
    if (cached == null) {
      // Nothing cached: the next loadWindow rebuilds from MySQL, which already
      // holds these rows — re-creating the cache here would just duplicate it.
      return;
    }
    List<com.hify.provider.adapter.ChatMessage> updated = new ArrayList<>(cached);
    Collections.addAll(updated, messages);
    redisUtil.set(key, updated, chatProperties.getContextTtl());
  }

  @Override
  public void evict(Long sessionId) {
    redisUtil.delete(contextKey(sessionId));
  }

  /** Completed messages only: GENERATING/FAILED rows carry partial or no text. */
  private List<com.hify.provider.adapter.ChatMessage> rebuildFromDb(Long sessionId, int turns) {
    int limit = turns * ROWS_PER_TURN + REBUILD_SLACK;
    List<ChatMessage> rows = chatMessageMapper.selectList(Wrappers.<ChatMessage>lambdaQuery()
        .eq(ChatMessage::getSessionId, sessionId)
        .eq(ChatMessage::getStatus, ChatConstants.MSG_STATUS_COMPLETED)
        .orderByDesc(ChatMessage::getId)
        .last("LIMIT " + limit));

    // Queried newest-first for the LIMIT, consumed oldest-first.
    Collections.reverse(rows);
    List<com.hify.provider.adapter.ChatMessage> window = new ArrayList<>(rows.size());
    for (ChatMessage row : rows) {
      com.hify.provider.adapter.ChatMessage message = toWindowMessage(row);
      if (message != null) {
        window.add(message);
      }
    }
    return window;
  }

  /**
   * Keep the newest {@code maxTurns} full turns. A turn starts at a user
   * message; everything from the maxTurns-th user message (counted from the
   * end) onward is kept, so the window never starts mid-turn — a tool result
   * separated from its assistant tool_calls would be rejected by the provider.
   */
  private List<com.hify.provider.adapter.ChatMessage> trimToTurns(
      List<com.hify.provider.adapter.ChatMessage> messages, int maxTurns) {
    int seen = 0;
    for (int i = messages.size() - 1; i >= 0; i--) {
      if (ChatConstants.ROLE_USER.equals(messages.get(i).getRole())) {
        seen++;
        if (seen >= maxTurns) {
          return i == 0 ? messages : new ArrayList<>(messages.subList(i, messages.size()));
        }
      }
    }
    return messages;
  }

  /** Null for roles the window does not carry (system lives in the agent config). */
  private com.hify.provider.adapter.ChatMessage toWindowMessage(ChatMessage row) {
    // TODO: once MCP tools land, restore toolCalls / toolCallId from row.meta —
    // dropping them here would break the tool_calls <-> tool pairing.
    if (ChatConstants.ROLE_ASSISTANT.equals(row.getRole())) {
      return com.hify.provider.adapter.ChatMessage.assistant(row.getContent(), null);
    }
    if (ChatConstants.ROLE_USER.equals(row.getRole())) {
      return com.hify.provider.adapter.ChatMessage.user(row.getContent());
    }
    return null;
  }

  private String contextKey(Long sessionId) {
    return ChatConstants.CONTEXT_KEY_PREFIX + sessionId;
  }
}
