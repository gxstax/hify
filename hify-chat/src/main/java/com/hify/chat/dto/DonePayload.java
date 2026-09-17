package com.hify.chat.dto;

/**
 * Payload of the terminal SSE {@code done} event: the reply is complete and
 * persisted. {@code finishReason} values mirror the provider
 * (stop / length / tool_calls) plus {@code cancelled} for a stop request.
 */
public record DonePayload(Long messageId, String finishReason,
    Integer promptTokens, Integer completionTokens) {
}
