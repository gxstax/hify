package com.hify.provider.service.impl;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.hify.provider.dto.ConnectionTestResult;
import com.hify.provider.entity.ProviderHealth;
import com.hify.provider.mapper.ProviderHealthMapper;
import com.hify.provider.service.ProviderHealthService;
import java.time.LocalDateTime;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/**
 * Health-row state machine:
 * <pre>
 *   probe ok    -> UP,      fail_count = 0, last_success_at = now
 *   probe fail  -> fail_count+1; status DEGRADED; DOWN once count >= 3
 * </pre>
 * last_check_at / latency_ms / error_message track the latest probe regardless
 * of outcome. No cache eviction here: the provider-cache detail is refreshed
 * by the regular probe cycle (worst case ~1 minute stale).
 */
@Service
@RequiredArgsConstructor
public class ProviderHealthServiceImpl implements ProviderHealthService {

  private static final String STATUS_UP = "UP";
  private static final String STATUS_DOWN = "DOWN";
  private static final String STATUS_DEGRADED = "DEGRADED";

  /** Consecutive failures after which the provider is marked DOWN. */
  private static final int DOWN_AFTER_FAILURES = 3;

  private final ProviderHealthMapper providerHealthMapper;

  @Override
  public void recordProbe(Long providerId, ConnectionTestResult result) {
    ProviderHealth health = providerHealthMapper.selectOne(
        Wrappers.<ProviderHealth>lambdaQuery()
            .eq(ProviderHealth::getProviderId, providerId));

    boolean existed = health != null;
    if (!existed) {
      health = new ProviderHealth();
      health.setProviderId(providerId);
      health.setFailCount(0);
    }

    LocalDateTime now = LocalDateTime.now();
    health.setLastCheckAt(now);
    health.setLatencyMs((int) result.getLatencyMs());

    if (result.isSuccess()) {
      health.setStatus(STATUS_UP);
      health.setFailCount(0);
      health.setLastSuccessAt(now);
      health.setErrorMessage(null);
    } else {
      int fails = (health.getFailCount() == null ? 0 : health.getFailCount()) + 1;
      health.setFailCount(fails);
      health.setStatus(fails >= DOWN_AFTER_FAILURES ? STATUS_DOWN : STATUS_DEGRADED);
      health.setErrorMessage(truncate(result.getErrorMessage()));
    }

    if (existed) {
      providerHealthMapper.updateById(health); // updated_at auto-filled
    } else {
      providerHealthMapper.insert(health);
    }
  }

  private String truncate(String s) {
    if (s == null || s.length() <= 500) {
      return s;
    }
    return s.substring(0, 500);
  }
}
