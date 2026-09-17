package com.hify.provider.adapter;

/**
 * Receives a streaming chat completion: text deltas as they arrive, then one
 * aggregated {@link ChatResponse} when the stream completes (carrying the
 * full text, any tool calls and usage). Errors surface as exceptions from the
 * blocking {@code streamChat} call, never through this callback.
 */
public interface ChatStreamCallback {

  /** One text increment, in arrival order. */
  void onDelta(String content);

  /** Stream finished: the aggregated result. Called exactly once on success. */
  void onComplete(ChatResponse aggregated);
}
