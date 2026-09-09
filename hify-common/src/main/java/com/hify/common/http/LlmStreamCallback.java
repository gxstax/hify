package com.hify.common.http;

/**
 * Receives one line of a streaming LLM response at a time. Empty separator
 * lines are already filtered out by {@link LlmHttpClient#stream}; providers
 * following the SSE convention yield lines like {@code data: {...}} and
 * {@code data: [DONE]} — parsing those belongs to the caller.
 */
@FunctionalInterface
public interface LlmStreamCallback {

  /** Invoked for every non-empty line, in arrival order, on the calling thread. */
  void onLine(String line);
}
