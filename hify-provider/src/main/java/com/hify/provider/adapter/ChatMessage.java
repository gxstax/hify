package com.hify.provider.adapter;

import java.util.List;
import lombok.Data;

/**
 * One message in a chat completion request — provider-neutral shape that each
 * adapter maps onto its own wire format.
 *
 * <p>Roles: {@code system | user | assistant | tool}. An assistant turn that
 * requests tools carries {@link #toolCalls} (content may be empty); each
 * {@code tool} turn answers one call via {@link #toolCallId}.
 */
@Data
public class ChatMessage {

  private String role;

  private String content;

  /** Assistant turns: tools the model wants to call. */
  private List<ToolCall> toolCalls;

  /** Tool turns: which call this message answers. */
  private String toolCallId;

  public static ChatMessage system(String content) {
    ChatMessage m = new ChatMessage();
    m.setRole("system");
    m.setContent(content);
    return m;
  }

  public static ChatMessage user(String content) {
    ChatMessage m = new ChatMessage();
    m.setRole("user");
    m.setContent(content);
    return m;
  }

  public static ChatMessage assistant(String content, List<ToolCall> toolCalls) {
    ChatMessage m = new ChatMessage();
    m.setRole("assistant");
    m.setContent(content);
    m.setToolCalls(toolCalls);
    return m;
  }

  public static ChatMessage tool(String toolCallId, String content) {
    ChatMessage m = new ChatMessage();
    m.setRole("tool");
    m.setToolCallId(toolCallId);
    m.setContent(content);
    return m;
  }
}
