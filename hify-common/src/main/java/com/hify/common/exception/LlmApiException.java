package com.hify.common.exception;

import lombok.Getter;

/**
 * Unified failure of an LLM API call. Every network/HTTP problem from
 * {@link com.hify.common.http.LlmHttpClient} is translated into this,
 * classified by {@link LlmErrorType} so callers can decide retry/backoff
 * without re-parsing raw exceptions (CLAUDE.md: retry by exception type —
 * network jitter retries, auth failures never retry, rate limits back off).
 */
@Getter
public class LlmApiException extends RuntimeException {

  private static final long serialVersionUID = 1L;

  public enum LlmErrorType {
    /** Connection/read deadline exceeded (client-side timeout). */
    TIMEOUT,
    /** 401/403: credential rejected — retrying makes no sense. */
    AUTH_FAILED,
    /** 429: rate limited — retry with backoff, respect Retry-After when given. */
    RATE_LIMITED,
    /** 400/404/422: the request itself is rejected (bad params, unknown model). */
    INVALID_REQUEST,
    /** 5xx: provider-side failure (retry decision lives in CircuitBreakerService). */
    SERVER_ERROR,
    /** Connection-level failure that is not a timeout (DNS, refused, stream broke). */
    NETWORK_ERROR,
  }

  private final LlmErrorType errorType;

  public LlmApiException(LlmErrorType errorType, String message) {
    super(message);
    this.errorType = errorType;
  }

  public LlmApiException(LlmErrorType errorType, String message, Throwable cause) {
    super(message, cause);
    this.errorType = errorType;
  }
}
