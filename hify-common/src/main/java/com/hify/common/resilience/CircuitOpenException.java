package com.hify.common.resilience;

import lombok.Getter;

/**
 * Thrown when the circuit breaker of a provider is OPEN: the call was rejected
 * without reaching the provider. Callers should fail fast — retrying while the
 * breaker is open is pointless; the breaker itself reopens after
 * waitDurationInOpenState.
 */
@Getter
public class CircuitOpenException extends RuntimeException {

  private static final long serialVersionUID = 1L;

  private final String providerName;

  public CircuitOpenException(String providerName, Throwable cause) {
    super("circuit breaker is open for provider: " + providerName, cause);
    this.providerName = providerName;
  }
}
