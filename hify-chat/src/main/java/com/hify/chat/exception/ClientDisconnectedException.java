package com.hify.chat.exception;

/**
 * Signals that the SSE client is gone (its socket refused a write).
 *
 * <p>Thrown from inside the {@code ChatStreamCallback} so it propagates out
 * through the provider adapter's read loop and unwinds
 * {@code LlmHttpClient.stream}'s try-with-resources — which closes the HTTP
 * connection and therefore cancels the upstream LLM call. Without this the
 * model would keep generating (and billing) for nobody.
 */
public class ClientDisconnectedException extends RuntimeException {

  private static final long serialVersionUID = 1L;

  public ClientDisconnectedException(Throwable cause) {
    super("chat client disconnected", cause);
  }
}
