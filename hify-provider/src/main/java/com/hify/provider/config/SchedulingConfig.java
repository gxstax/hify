package com.hify.provider.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * Enables the scheduler backing @Scheduled tasks (provider health probes).
 * The scheduler itself stays single-threaded; long probe work is dispatched
 * to the shared {@code asyncExecutor} by HealthCheckTask.
 */
@Configuration
@EnableScheduling
public class SchedulingConfig {
}
