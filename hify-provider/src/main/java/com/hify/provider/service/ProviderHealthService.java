package com.hify.provider.service;

import com.hify.provider.dto.ConnectionTestResult;

/**
 * Persists connectivity probe outcomes into provider_health (one row per
 * provider, upsert). Shared by the scheduled health check and the manual
 * test-connection flow.
 */
public interface ProviderHealthService {

  /**
   * Apply one probe result to the provider's health row:
   * success -> UP, fail_count reset, last_success_at refreshed;
   * failure  -> fail_count + 1, status DEGRADED until 3 consecutive failures
   *             flip it to DOWN (errorMessage keeps the last reason).
   */
  void recordProbe(Long providerId, ConnectionTestResult result);
}
