package com.hify.chat.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

/** Body of {@code POST /api/v1/chat-sessions} — creates an empty session. */
@Data
public class CreateSessionReq {

  /** Agent the new session talks to. */
  @NotNull(message = "agentId 不能为空")
  private Long agentId;

  /** Optional display name; when omitted it is derived from the first user
   *  message once one arrives. Either way it is stored as a single line of at
   *  most 30 characters — longer input is truncated, not rejected. (The 128
   *  here only guards the request body against abuse.) */
  @Size(max = 128, message = "title 长度不能超过 128 字符")
  private String title;
}
