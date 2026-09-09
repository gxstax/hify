package com.hify.common.resilience;

import com.hify.common.exception.LlmApiException;
import com.hify.common.exception.LlmApiException.LlmErrorType;
import io.github.resilience4j.circuitbreaker.CallNotPermittedException;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import java.util.concurrent.Callable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * One independent circuit breaker per LLM provider.
 *
 * <p>Breaker parameters come from application.yml ({@code resilience4j.
 * circuitbreaker.configs.default}: sliding window 10, 50% failure rate, 30s
 * open, 3 calls in half-open) — the {@link CircuitBreakerRegistry} bean is
 * built from them by the Resilience4j Spring Boot autoconfiguration and each
 * provider name maps to its own breaker instance with independent state.
 *
 * <p>{@link #executeWithRetry} wraps a call with retry rules per CLAUDE.md:
 * <ul>
 *   <li>TIMEOUT / NETWORK_ERROR: retry up to 2 times, 1s apart</li>
 *   <li>RATE_LIMITED: retry up to 2 times with backoff 2s then 4s</li>
 *   <li>anything else (AUTH_FAILED, INVALID_REQUEST, SERVER_ERROR): no retry</li>
 * </ul>
 * Every attempt counts against the breaker, so a provider failing repeatedly
 * trips its own breaker after the configured window.
 */
@Component
public class CircuitBreakerService {

  private static final Logger log = LoggerFactory.getLogger(CircuitBreakerService.class);

  /** Max retries per call (call runs at most 3 times). */
  private static final int MAX_RETRIES = 2;

  private static final long RETRY_DELAY_NETWORK_MS = 1000;
  private static final long RETRY_DELAY_RATE_LIMIT_BASE_MS = 2000;

  private final CircuitBreakerRegistry circuitBreakerRegistry;

  public CircuitBreakerService(CircuitBreakerRegistry circuitBreakerRegistry) {
    this.circuitBreakerRegistry = circuitBreakerRegistry;
  }

  /** Get (or lazily create) the breaker for a provider, built on the default config. */
  public CircuitBreaker breaker(String providerName) {
    return circuitBreakerRegistry.circuitBreaker(providerName);
  }

  /**
   * Execute a provider call under its circuit breaker with type-aware retry.
   *
   * @throws CircuitOpenException when the breaker is open
   * @throws LlmApiException when the call fails after retries (or is non-retriable)
   */
  public <T> T executeWithRetry(String providerName, Callable<T> callable) {
    int attempts = 0; // failed attempts so far
    while (true) {
      try {
        return breaker(providerName).executeCallable(callable);
      } catch (CallNotPermittedException e) {
        throw new CircuitOpenException(providerName, e);
      } catch (LlmApiException e) {
        long delayMs = retryDelayMs(e.getErrorType(), attempts);
        if (delayMs < 0 || attempts >= MAX_RETRIES) {
          throw e;
        }
        attempts++;
        log.warn("llm call failed provider={} attempts={} type={} retry_in_ms={}",
            providerName, attempts, e.getErrorType(), delayMs);
        sleep(delayMs);
      } catch (Exception e) {
        // Residual checked exceptions from the callable
        throw new RuntimeException("llm call failed: provider=" + providerName, e);
      }
    }
  }

  /** Delay before the next retry, or -1 when the error type must not retry. */
  private long retryDelayMs(LlmErrorType type, int failedAttempts) {
    return switch (type) {
      case TIMEOUT, NETWORK_ERROR -> RETRY_DELAY_NETWORK_MS;
      // Backoff grows with the attempt: 2s before the 1st retry, 4s before the 2nd
      case RATE_LIMITED -> RETRY_DELAY_RATE_LIMIT_BASE_MS * (failedAttempts + 1);
      // AUTH_FAILED, INVALID_REQUEST, SERVER_ERROR: retrying won't help
      default -> -1;
    };
  }

  private void sleep(long delayMs) {
    try {
      Thread.sleep(delayMs);
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      throw new LlmApiException(LlmErrorType.TIMEOUT, "interrupted while retrying", e);
    }
  }
}
