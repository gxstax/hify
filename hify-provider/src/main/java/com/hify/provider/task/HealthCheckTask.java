package com.hify.provider.task;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.hify.provider.dto.ConnectionTestResult;
import com.hify.provider.entity.Provider;
import com.hify.provider.mapper.ProviderMapper;
import com.hify.provider.service.ProviderHealthService;
import com.hify.provider.service.ProviderService;
import java.util.List;
import java.util.concurrent.RejectedExecutionException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Periodic health probe of every enabled provider.
 *
 * <p>Runs once per minute on the (single-threaded) scheduler, but only picks
 * the provider list and dispatches one async probe per provider onto the
 * shared {@code asyncExecutor} (CLAUDE.md) — providers are probed in
 * parallel (each capped at the 10s probe timeout) and the scheduler thread
 * never blocks. {@code fixedDelay} guarantees the rounds never overlap.
 *
 * <p>Note: the task package is a small extension of the standard module
 * layout for scheduled jobs; fold into service/ if the convention tightens.
 */
@Component
public class HealthCheckTask {

  private static final Logger log = LoggerFactory.getLogger(HealthCheckTask.class);

  private final ProviderMapper providerMapper;
  private final ProviderService providerService;
  private final ProviderHealthService providerHealthService;
  private final ThreadPoolTaskExecutor asyncExecutor;

  public HealthCheckTask(ProviderMapper providerMapper,
      ProviderService providerService,
      ProviderHealthService providerHealthService,
      @Qualifier("asyncExecutor") ThreadPoolTaskExecutor asyncExecutor) {
    this.providerMapper = providerMapper;
    this.providerService = providerService;
    this.providerHealthService = providerHealthService;
    this.asyncExecutor = asyncExecutor;
  }

  @Scheduled(fixedDelay = 60_000)
  public void probeAllEnabledProviders() {
    List<Provider> providers = providerMapper.selectList(
        Wrappers.<Provider>lambdaQuery().eq(Provider::getEnabled, true));
    if (providers.isEmpty()) {
      return;
    }
    log.info("health-check: dispatching probes for {} enabled provider(s)", providers.size());

    for (Provider provider : providers) {
      Long providerId = provider.getId();
      try {
        asyncExecutor.execute(() -> probeAndRecord(providerId));
      } catch (RejectedExecutionException e) {
        // Queue full — skip this round for that provider instead of failing the scan
        log.warn("health-check: asyncExecutor saturated, skipping provider {}", providerId);
      }
    }
  }

  private void probeAndRecord(Long providerId) {
    try {
      ConnectionTestResult result = providerService.testConnection(providerId);
      providerHealthService.recordProbe(providerId, result);
      log.info("health-check: provider={} success={} latency_ms={} models={} msg={}",
          providerId, result.isSuccess(), result.getLatencyMs(), result.getModelCount(),
          result.getErrorMessage());
    } catch (Exception e) {
      log.error("health-check: probe failed unexpectedly for provider {}", providerId, e);
    }
  }
}
