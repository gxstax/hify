package com.hify.provider.service;

/** Synchronous one-shot LLM invocation facade for other modules. */
public interface LlmInvocationService {

  /**
   * Invoke an enabled model with a single user message and return its text content.
   *
   * @param modelConfigId model configuration id
   * @param prompt user message content
   * @return non-blank assistant content
   */
  String invoke(Long modelConfigId, String prompt);
}
