package com.hify.chat.dto;

/**
 * Payload of the terminal SSE {@code error} event.
 *
 * <p>The HTTP status was already committed as 200 when the stream started, so
 * failures can only travel as an event — clients must treat {@code done} and
 * {@code error} as the two possible ends of a stream.
 */
public record ErrorPayload(int code, String message) {
}
