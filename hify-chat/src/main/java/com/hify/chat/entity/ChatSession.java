package com.hify.chat.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.hify.common.entity.BaseEntity;
import java.time.LocalDateTime;
import lombok.Getter;
import lombok.Setter;

/**
 * One conversation with an agent. {@code lastMessageAt} / {@code messageCount}
 * are maintained on every message write so the session list never has to
 * aggregate the (much larger) message table.
 */
@Getter
@Setter
@TableName("chat_session")
public class ChatSession extends BaseEntity {

  /** agent.id (app-layer FK). */
  private Long agentId;

  /** Derived from the first user message. */
  private String title;

  /** ACTIVE / ARCHIVED. */
  private String status;

  /** Last activity, drives list ordering; NULL until the first message. */
  private LocalDateTime lastMessageAt;

  /** Number of messages written so far. */
  private Integer messageCount;

  /** Excerpt of the newest message, shown as the second line of the session
   *  list card. Kept denormalized so listing sessions never touches
   *  chat_message. */
  private String lastMessagePreview;
}
