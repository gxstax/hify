package com.hify.chat.exception;

/**
 * Signals that the stream must stop producing: the SseEmitter timed out (its
 * {@code onTimeout} callback set the cancel flag) or was already completed.
 *
 * <p>Like {@link ClientDisconnectedException} it is thrown from inside the
 * stream callback so it aborts the upstream LLM call instead of being swallowed.
 */
public class StreamCancelledException extends RuntimeException {

  private static final long serialVersionUID = 1L;

  public StreamCancelledException(String message) {
    super(message);
  }

  public StreamCancelledException(String message, Throwable cause) {
    super(message, cause);
  }
}
