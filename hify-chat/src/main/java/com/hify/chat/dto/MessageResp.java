package com.hify.chat.dto;

import com.hify.chat.entity.ChatMessage;
import java.time.LocalDateTime;
import lombok.Data;

/**
 * Message row for history rendering. {@code status} matters to the client:
 * FAILED turns keep the partial text and must render as "generation failed",
 * and GENERATING rows are leftovers from an interrupted stream.
 */
@Data
public class MessageResp {

  private Long id;
  private Long sessionId;
  private Long parentId;
  private String role;
  private String content;
  private String status;
  private String finishReason;
  private Integer promptTokens;
  private Integer completionTokens;
  private LocalDateTime createdAt;

  public static MessageResp of(ChatMessage message) {
    MessageResp resp = new MessageResp();
    resp.setId(message.getId());
    resp.setSessionId(message.getSessionId());
    resp.setParentId(message.getParentId());
    resp.setRole(message.getRole());
    resp.setContent(message.getContent());
    resp.setStatus(message.getStatus());
    resp.setFinishReason(message.getFinishReason());
    resp.setPromptTokens(message.getPromptTokens());
    resp.setCompletionTokens(message.getCompletionTokens());
    resp.setCreatedAt(message.getCreatedAt());
    return resp;
  }
}
