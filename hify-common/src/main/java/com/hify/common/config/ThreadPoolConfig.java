package com.hify.common.config;

import java.util.concurrent.ThreadPoolExecutor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

/**
 * Shared thread pools. CLAUDE.md rules: LLM calls must run on
 * {@code llmExecutor} (injected via {@code @Qualifier("llmExecutor")}) and
 * fire-and-forget jobs on {@code asyncExecutor}; never {@code new Thread()}
 * or the default executor.
 *
 * <pre>{@code
 * @Autowired @Qualifier("llmExecutor")
 * private ThreadPoolTaskExecutor llmExecutor;
 * }</pre>
 *
 * <p>Sizes target the ~50-user deployment. The default rejected-execution
 * handler of ThreadPoolTaskExecutor is AbortPolicy (throws); override per
 * pool below if tasks may outgrow the queue.
 */
@Configuration
public class ThreadPoolConfig {

  /** LLM (provider API) calls: dedicated pool, isolated from request threads. */
  @Bean
  public ThreadPoolTaskExecutor llmExecutor() {
    ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
    executor.setCorePoolSize(10);
    executor.setMaxPoolSize(50);
    executor.setQueueCapacity(100);
    executor.setThreadNamePrefix("llm-");
    // Backpressure instead of rejection: run on the caller thread when saturated
    executor.setRejectedExecutionHandler(new ThreadPoolExecutor.CallerRunsPolicy());
    return executor;
  }

  /** Non-critical async jobs (async log writes, housekeeping, ...). */
  @Bean
  public ThreadPoolTaskExecutor asyncExecutor() {
    ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
    executor.setCorePoolSize(5);
    executor.setMaxPoolSize(20);
    executor.setQueueCapacity(200);
    executor.setThreadNamePrefix("async-");
    return executor;
  }
}
