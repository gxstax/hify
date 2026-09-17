package com.hify.chat.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

/** Body of {@code POST /api/v1/chat-sessions/{id}/messages} — the session
 *  comes from the path, only the message text travels here. */
@Data
public class SendMessageReq {

  /** User message; capped to keep a single turn from blowing up the context
   *  window (and the row) in one shot. */
  @NotBlank(message = "content 不能为空")
  @Size(max = 32000, message = "content 长度不能超过 32000 字符")
  private String content;
}
