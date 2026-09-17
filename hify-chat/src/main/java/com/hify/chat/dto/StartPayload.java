package com.hify.chat.dto;

/**
 * Payload of the first SSE event of every stream: which session and which
 * assistant message the following deltas belong to.
 *
 * <p>Needed because a new session's id is generated server-side — without it
 * the client would only learn the id from {@code done}, and would lose it
 * entirely when the stream ends in {@code error}.
 */
public record StartPayload(Long sessionId, Long messageId) {
}
