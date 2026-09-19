package com.hify.chat.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.hify.agent.dto.AgentDetailResponse;
import com.hify.agent.service.AgentService;
import com.hify.chat.config.ChatProperties;
import com.hify.chat.constant.ChatConstants;
import com.hify.chat.dto.DeltaPayload;
import com.hify.chat.dto.DonePayload;
import com.hify.chat.dto.ErrorPayload;
import com.hify.chat.dto.MessageResp;
import com.hify.chat.dto.SessionResp;
import com.hify.chat.dto.StartPayload;
import com.hify.chat.entity.ChatSession;
import com.hify.chat.exception.ClientDisconnectedException;
import com.hify.chat.exception.StreamCancelledException;
import com.hify.chat.mapper.ChatMessageMapper;
import com.hify.chat.mapper.ChatSessionMapper;
import com.hify.chat.service.ChatContextService;
import com.hify.chat.service.ChatPersistenceService;
import com.hify.chat.service.ChatService;
import com.hify.common.dto.PageResult;
import com.hify.common.exception.BizException;
import com.hify.common.exception.ErrorCode;
import com.hify.common.exception.LlmApiException;
import com.hify.common.util.PageHelper;
import com.hify.provider.adapter.ChatMessage;
import com.hify.provider.adapter.ChatRequest;
import com.hify.provider.adapter.ChatResponse;
import com.hify.provider.adapter.ChatStreamCallback;
import com.hify.provider.adapter.ProviderAdapter;
import com.hify.provider.adapter.ProviderAdapterFactory;
import com.hify.provider.dto.ModelConfigBrief;
import com.hify.provider.entity.Provider;
import com.hify.provider.service.ModelConfigService;
import com.hify.provider.service.ProviderService;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.MediaType;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

/**
 * Chat engine: assembles the request from the agent config + context window,
 * persists the turn, and streams the reply over SSE.
 *
 * <p><b>Transactions.</b> The streaming method itself is deliberately NOT
 * transactional and never will be: the SSE connection stays open for up to a
 * minute, and a transaction spanning it would pin one of the 10 Hikari
 * connections for the whole stream. Every write goes through
 * {@link ChatPersistenceService}, whose methods are short transaction units of
 * their own — which also means they must be a separate bean, since a
 * self-invocation inside this class would silently bypass the proxy.
 *
 * <p><b>Threading.</b> {@code sendMessage} runs on a Tomcat request thread and
 * returns immediately after handing the work to {@code llmExecutor}; the
 * stream then runs entirely on that pool thread (deltas arrive as synchronous
 * callbacks of the blocking adapter call). Emitter callbacks (timeout/error)
 * fire on container threads and only set the {@code cancelled} flag — the
 * streaming thread owns the message row and does all the persisting.
 */
@Slf4j
@Service
public class ChatServiceImpl implements ChatService {

  /** Hard cap on a message history page (mirrors PageHelper.MAX_PAGE_SIZE). */
  private static final int MAX_MESSAGE_PAGE_SIZE = 100;

  private final ChatPersistenceService chatPersistence;
  private final ChatContextService chatContext;
  private final AgentService agentService;
  private final ModelConfigService modelConfigService;
  private final ProviderService providerService;
  private final ProviderAdapterFactory adapterFactory;
  private final ChatProperties chatProperties;
  private final ChatSessionMapper chatSessionMapper;
  private final ChatMessageMapper chatMessageMapper;
  private final ThreadPoolTaskExecutor llmExecutor;

  /**
   * Hand-written instead of {@code @RequiredArgsConstructor}: Lombok only
   * copies {@code @Qualifier} onto constructor parameters when
   * {@code lombok.copyableAnnotations} is configured, and without it the two
   * ThreadPoolTaskExecutor beans (llmExecutor / asyncExecutor) make the
   * injection ambiguous.
   */
  public ChatServiceImpl(ChatPersistenceService chatPersistence,
      ChatContextService chatContext,
      AgentService agentService,
      ModelConfigService modelConfigService,
      ProviderService providerService,
      ProviderAdapterFactory adapterFactory,
      ChatProperties chatProperties,
      ChatSessionMapper chatSessionMapper,
      ChatMessageMapper chatMessageMapper,
      @Qualifier("llmExecutor") ThreadPoolTaskExecutor llmExecutor) {
    this.chatPersistence = chatPersistence;
    this.chatContext = chatContext;
    this.agentService = agentService;
    this.modelConfigService = modelConfigService;
    this.providerService = providerService;
    this.adapterFactory = adapterFactory;
    this.chatProperties = chatProperties;
    this.chatSessionMapper = chatSessionMapper;
    this.chatMessageMapper = chatMessageMapper;
    this.llmExecutor = llmExecutor;
  }

  @Override
  public SessionResp createSession(Long agentId, String title) {
    // Validate before writing anything: an unusable agent must not leave a
    // stray empty session behind.
    requireEnabledAgent(agentId);
    return SessionResp.of(chatPersistence.createSession(agentId, title));
  }

  @Override
  public SseEmitter sendMessage(Long sessionId, String content) {
    // Fail fast before anything is written: an unknown session, a disabled
    // agent and a disabled model all surface as plain HTTP errors instead of
    // failing halfway through a stream that already returned 200.
    ChatSession session = chatPersistence.requireSession(sessionId);
    AgentDetailResponse agent = requireEnabledAgent(session.getAgentId());

    // Resolve the whole model chain up front as well — a disabled model or an
    // unknown provider must surface as a normal HTTP error, not as a failure
    // halfway through a stream that already returned 200.
    ModelConfigBrief model = modelConfigService.requireUsable(agent.getModelConfigId());
    Provider provider = providerService.getDetail(model.getProviderId()).getProvider();
    ProviderAdapter adapter = adapterFactory.getAdapter(provider.getType());

    // Short transactions, one per write (see the class comment).
    com.hify.chat.entity.ChatMessage userMessage =
        chatPersistence.saveUserMessage(session.getId(), content);
    com.hify.chat.entity.ChatMessage assistantMessage =
        chatPersistence.createAssistantPlaceholder(session.getId(), userMessage.getId());

    // The user turn is stored now but the cached window predates it: append it
    // before loading (on a cache miss the rebuild reads the row from MySQL
    // anyway, and append is then a no-op).
    chatContext.append(session.getId(), ChatMessage.user(content));

    int maxTurns = agent.getMaxContextTurns() == null
        ? ChatConstants.DEFAULT_MAX_CONTEXT_TURNS : agent.getMaxContextTurns();
    List<ChatMessage> window = chatContext.loadWindow(session.getId(), maxTurns);

    SseEmitter emitter = new SseEmitter(chatProperties.getSseTimeoutMs());
    StreamTask task = new StreamTask(emitter, adapter, provider,
        buildRequest(agent, model, window), session.getId(), assistantMessage.getId());
    emitter.onTimeout(task::onTimeout);
    emitter.onError(task::onError);
    llmExecutor.execute(task);
    return emitter;
  }

  /** system prompt (from the agent, never cached) + the window, oldest first. */
  private ChatRequest buildRequest(AgentDetailResponse agent, ModelConfigBrief model,
      List<ChatMessage> window) {
    ChatRequest request = new ChatRequest();
    request.setModel(model.getModelId());
    List<ChatMessage> messages = new ArrayList<>(window.size() + 1);
    if (agent.getSystemPrompt() != null && !agent.getSystemPrompt().isBlank()) {
      messages.add(ChatMessage.system(agent.getSystemPrompt()));
    }
    messages.addAll(window);
    request.setMessages(messages);
    request.setTemperature(agent.getTemperature());
    request.setMaxTokens(agent.getMaxTokens());
    return request;
  }

  // ------------------------------------------------------------------
  // Session management
  // ------------------------------------------------------------------

  @Override
  public PageResult<SessionResp> listSessions(int page, int pageSize, Long agentId) {
    Page<ChatSession> pageParam = PageHelper.toPage(page, pageSize);
    LambdaQueryWrapper<ChatSession> wrapper = Wrappers.<ChatSession>lambdaQuery()
        .eq(agentId != null, ChatSession::getAgentId, agentId)
        // NULL last_message_at (created but never used) sorts last under DESC.
        .orderByDesc(ChatSession::getLastMessageAt)
        .orderByDesc(ChatSession::getId);
    Page<ChatSession> result = chatSessionMapper.selectPage(pageParam, wrapper);
    List<SessionResp> list = result.getRecords().stream().map(SessionResp::of).toList();
    return new PageResult<>(list, result.getTotal(), (int) result.getCurrent(),
        (int) result.getSize());
  }

  @Override
  public SessionResp getSession(Long id) {
    ChatSession session = chatSessionMapper.selectById(id);
    if (session == null) {
      throw new BizException(ErrorCode.NOT_FOUND, "会话不存在: " + id);
    }
    return SessionResp.of(session);
  }

  @Override
  public List<MessageResp> listMessages(Long sessionId, Long cursor, int limit) {
    // Fail loudly on an unknown session: an empty page would read as "no
    // history yet" instead of "no such session".
    getSession(sessionId);

    int size = Math.min(Math.max(limit, 1), MAX_MESSAGE_PAGE_SIZE);
    // Queried newest-first so the LIMIT keeps the newest page; chat UIs render
    // oldest-first, so flip before returning.
    List<com.hify.chat.entity.ChatMessage> rows = selectMessagePage(sessionId, cursor, size);
    Collections.reverse(rows);
    return rows.stream().map(MessageResp::of).toList();
  }

  @Override
  public void deleteSession(Long id) {
    chatPersistence.deleteSession(id);
    // Drop the cached window too: otherwise it would outlive its session for
    // up to the TTL and resurface if the id were reused.
    chatContext.evict(id);
  }

  /** Oldest/id-descending page of message rows (the entity, not the wire DTO). */
  private List<com.hify.chat.entity.ChatMessage> selectMessagePage(
      Long sessionId, Long cursor, int limit) {
    return chatMessageMapper.selectList(Wrappers.<com.hify.chat.entity.ChatMessage>lambdaQuery()
        .eq(com.hify.chat.entity.ChatMessage::getSessionId, sessionId)
        .lt(cursor != null, com.hify.chat.entity.ChatMessage::getId, cursor)
        .orderByDesc(com.hify.chat.entity.ChatMessage::getId)
        .last("LIMIT " + limit));
  }

  private AgentDetailResponse requireEnabledAgent(Long agentId) {
    if (agentId == null) {
      throw new BizException(ErrorCode.PARAM_ERROR, "新建会话时必须指定 agentId");
    }
    AgentDetailResponse agent = agentService.getAgent(agentId);
    if (!Boolean.TRUE.equals(agent.getEnabled())) {
      throw new BizException(ErrorCode.PARAM_ERROR, "Agent 已禁用，无法对话: " + agentId);
    }
    return agent;
  }

  /**
   * One streaming call. Runs on an {@code llmExecutor} thread; the emitter's
   * timeout/error callbacks run on container threads, so the two sides only
   * communicate through {@link #cancelled} — the row itself is touched by this
   * thread alone.
   */
  private final class StreamTask implements Runnable {

    private final SseEmitter emitter;
    private final ProviderAdapter adapter;
    private final Provider provider;
    private final ChatRequest request;
    private final Long sessionId;
    private final Long assistantMessageId;

    /** Text produced so far; kept even when the stream fails midway. */
    private final StringBuilder accumulated = new StringBuilder();

    /** Set by the emitter callbacks (container threads), read by the stream. */
    private final AtomicBoolean cancelled = new AtomicBoolean(false);

    StreamTask(SseEmitter emitter, ProviderAdapter adapter, Provider provider,
        ChatRequest request, Long sessionId, Long assistantMessageId) {
      this.emitter = emitter;
      this.adapter = adapter;
      this.provider = provider;
      this.request = request;
      this.sessionId = sessionId;
      this.assistantMessageId = assistantMessageId;
    }

    @Override
    public void run() {
      try {
        // Announce the ids before any content: for a new session the client
        // cannot know them otherwise (and would lose the session entirely if
        // the stream failed before the done event).
        try {
          sendEvent("start", new StartPayload(sessionId, assistantMessageId));
        } catch (IOException e) {
          throw new ClientDisconnectedException(e);
        }
        adapter.streamChat(provider, request, new ChatStreamCallback() {
          @Override
          public void onDelta(String content) {
            if (cancelled.get()) {
              // Emitter timed out: stop producing and abort the upstream call.
              throw new StreamCancelledException("emitter timed out");
            }
            accumulated.append(content);
            try {
              sendEvent("delta", new DeltaPayload(content));
            } catch (IOException e) {
              // Client is gone. Propagating aborts the upstream LLM call (see
              // ClientDisconnectedException) instead of paying for tokens
              // nobody will read.
              throw new ClientDisconnectedException(e);
            } catch (IllegalStateException e) {
              // Emitter already completed by the container (timeout race).
              throw new StreamCancelledException("emitter already completed", e);
            }
          }

          @Override
          public void onComplete(ChatResponse response) {
            complete(response);
          }
        });
      } catch (ClientDisconnectedException e) {
        onClientGone(e);
      } catch (Exception e) {
        onFailure(e);
      }
    }

    private void complete(ChatResponse response) {
      String full = accumulated.length() > 0 ? accumulated.toString() : response.getContent();
      // Persist before notifying: the DB is the source of truth and the client
      // may already be gone by the time the done event is written.
      chatPersistence.finishAssistantMessage(sessionId, assistantMessageId, full,
          response.getFinishReason(), response.getPromptTokens(), response.getCompletionTokens());
      // Normalize empty tool lists to null: adapters hand back List.of() when
      // the model answered directly, and caching that puts Jackson's internal
      // ImmutableCollections$ListN type name into the Redis value.
      chatContext.append(sessionId, ChatMessage.assistant(full,
          response.hasToolCalls() ? response.getToolCalls() : null));

      try {
        sendEvent("done", new DonePayload(assistantMessageId, response.getFinishReason(),
            response.getPromptTokens(), response.getCompletionTokens()));
      } catch (Exception e) {
        log.debug("client gone before the done event, sessionId={}", sessionId);
      }
      emitter.complete();
    }

    /** Client vanished: the upstream call is already aborted, settle the row. */
    private void onClientGone(ClientDisconnectedException e) {
      log.info("client disconnected mid-stream, sessionId={}, messageId={}",
          sessionId, assistantMessageId);
      chatPersistence.failAssistantMessage(sessionId, assistantMessageId, accumulated.toString());
      // The socket is dead; completeWithError just releases emitter resources.
      emitter.completeWithError(e);
    }

    /** Provider error, timeout or cancel: record the partial text, end the stream. */
    private void onFailure(Exception e) {
      log.warn("stream failed, sessionId={}, messageId={}", sessionId, assistantMessageId, e);
      chatPersistence.failAssistantMessage(sessionId, assistantMessageId, accumulated.toString());

      if (cancelled.get()) {
        // Timed out: nobody is listening any more, just release resources.
        emitter.completeWithError(e);
        return;
      }
      try {
        // An error event, not completeWithError: the response is already
        // committed as 200/text-event-stream, so re-dispatching the exception
        // to @ExceptionHandler would append a JSON error body into the live
        // SSE stream. Clients learn the outcome from done/error instead.
        sendEvent("error", errorPayload(e));
        emitter.complete();
      } catch (Exception sendFailure) {
        // Even the error event could not be delivered — last resort.
        emitter.completeWithError(e);
      }
    }

    private ErrorPayload errorPayload(Exception e) {
      String message = e instanceof LlmApiException llm
          ? "模型调用失败: " + llm.getErrorType()
          : e.getMessage();
      return new ErrorPayload(ErrorCode.SYSTEM_ERROR.getCode(), message);
    }

    private void sendEvent(String name, Object payload) throws IOException {
      emitter.send(SseEmitter.event().name(name).data(payload, MediaType.APPLICATION_JSON));
    }

    /**
     * Emitter timeout (container thread). Only raises the flag: the streaming
     * thread notices it on the next delta and aborts; if it is blocked inside
     * the provider read, the provider's own timeout ends the call. Persisting
     * from here would race with that thread.
     */
    void onTimeout() {
      log.warn("sse timeout, sessionId={}, messageId={}", sessionId, assistantMessageId);
      cancelled.set(true);
    }

    void onError(Throwable e) {
      log.debug("sse error, sessionId={}: {}", sessionId, e.getMessage());
      cancelled.set(true);
    }
  }
}
