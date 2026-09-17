package com.hify.chat.controller;

import com.hify.chat.dto.CreateSessionReq;
import com.hify.chat.dto.MessageResp;
import com.hify.chat.dto.SendMessageReq;
import com.hify.chat.dto.SessionResp;
import com.hify.chat.service.ChatService;
import com.hify.common.dto.PageResult;
import com.hify.common.dto.Result;
import jakarta.validation.Valid;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

/**
 * Chat endpoints. Controllers only validate and delegate (CLAUDE.md).
 *
 * <p>Two response shapes live here: the streaming endpoints answer with a live
 * SSE stream (so validation errors are still normal HTTP errors — they happen
 * before any byte is streamed — while anything failing after the stream
 * started travels as an {@code error} event, the status code being committed
 * as 200 by then), and the management endpoints answer with the usual
 * {@link Result} envelope.
 */
@RestController
@RequestMapping("/api/v1/chat/sessions")
@RequiredArgsConstructor
public class ChatController {

  private final ChatService chatService;

  // ------------------------------------------------------------------
  // Streaming
  // ------------------------------------------------------------------

  /**
   * Create an empty session. Not streaming: the client gets the session id
   * back immediately (to navigate to it, or to show it in the list) and then
   * sends messages through {@code POST /{id}/messages}.
   */
  @PostMapping
  public Result<SessionResp> createSession(@Valid @RequestBody CreateSessionReq req) {
    return Result.ok(chatService.createSession(req.getAgentId(), req.getTitle()));
  }

  /** Continue an existing session: POST on its messages sub-resource. */
  @PostMapping(value = "/{id}/messages", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
  public SseEmitter sendMessage(@PathVariable Long id, @Valid @RequestBody SendMessageReq req) {
    return chatService.sendMessage(id, req.getContent());
  }

  // ------------------------------------------------------------------
  // Session management
  // ------------------------------------------------------------------

  /** Page through sessions, most recently active first; optional agent filter. */
  @GetMapping
  public Result<PageResult<SessionResp>> listSessions(
      @RequestParam(defaultValue = "1") int page,
      @RequestParam(defaultValue = "20") int pageSize,
      @RequestParam(required = false) Long agentId) {
    return Result.ok(chatService.listSessions(page, pageSize, agentId));
  }

  /** Detail of one session. */
  @GetMapping("/{id}")
  public Result<SessionResp> getSession(@PathVariable Long id) {
    return Result.ok(chatService.getSession(id));
  }

  /**
   * Message history, oldest first. Pass the id of the oldest message already
   * held as {@code cursor} to fetch the page before it; omit for the newest
   * page. A page shorter than {@code limit} marks the start of the history.
   */
  @GetMapping("/{id}/messages")
  public Result<List<MessageResp>> listMessages(
      @PathVariable Long id,
      @RequestParam(required = false) Long cursor,
      @RequestParam(defaultValue = "50") int limit) {
    return Result.ok(chatService.listMessages(id, cursor, limit));
  }

  /** Delete a session and its messages (messages are removed physically). */
  @DeleteMapping("/{id}")
  public Result<Void> deleteSession(@PathVariable Long id) {
    chatService.deleteSession(id);
    return Result.ok();
  }
}
