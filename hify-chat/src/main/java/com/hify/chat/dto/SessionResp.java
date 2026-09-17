package com.hify.chat.dto;

import com.hify.chat.entity.ChatSession;
import java.time.LocalDateTime;
import lombok.Data;

/**
 * Session row for the console list/detail. Carries {@code agentId} only — the
 * client maps it against the agent list it already loads, so this endpoint
 * stays free of cross-module joins.
 */
@Data
public class SessionResp {

  private Long id;
  private Long agentId;
  private String title;
  private String status;
  private Integer messageCount;
  private LocalDateTime lastMessageAt;
  private LocalDateTime createdAt;
  private LocalDateTime updatedAt;

  public static SessionResp of(ChatSession session) {
    SessionResp resp = new SessionResp();
    resp.setId(session.getId());
    resp.setAgentId(session.getAgentId());
    resp.setTitle(session.getTitle());
    resp.setStatus(session.getStatus());
    resp.setMessageCount(session.getMessageCount());
    resp.setLastMessageAt(session.getLastMessageAt());
    resp.setCreatedAt(session.getCreatedAt());
    resp.setUpdatedAt(session.getUpdatedAt());
    return resp;
  }
}
