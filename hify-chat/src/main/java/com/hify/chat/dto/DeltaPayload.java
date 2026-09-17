package com.hify.chat.dto;

/**
 * Payload of an SSE {@code delta} event — one text increment of the reply.
 * Serialized to JSON by the message converter; must stay single-line, SSE
 * frames break on raw newlines.
 */
public record DeltaPayload(String content) {
}
