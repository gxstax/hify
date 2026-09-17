package com.hify.chat.config;

import java.time.Duration;
import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/** Chat-module settings, externalized per CLAUDE.md (no hardcoded values). */
@Getter
@Setter
@Component
@ConfigurationProperties(prefix = "hify.chat")
public class ChatProperties {

  /**
   * SseEmitter timeout in ms. Must exceed the conversation budget (60s per
   * CLAUDE.md) so the business timeout fires first and can close the stream
   * cleanly; without an explicit value the container default (30s on Tomcat)
   * would cut long replies short.
   */
  private long sseTimeoutMs = 65_000L;

  /** TTL of the context window cache (session:{sessionId}). */
  private Duration contextTtl = Duration.ofHours(2);
}
