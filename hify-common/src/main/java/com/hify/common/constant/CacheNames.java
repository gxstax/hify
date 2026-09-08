package com.hify.common.constant;

/**
 * Spring Cache cache names, one per cached domain. TTLs are configured in
 * {@link com.hify.common.config.CacheConfig}; keep the two in sync when adding
 * a cache. Annotation usage: {@code @Cacheable(cacheNames = CacheNames.PROVIDER)}.
 */
public final class CacheNames {

  /** Model provider / model_config reads (TTL 30min). */
  public static final String PROVIDER = "provider-cache";

  /** Agent configuration reads (TTL 30min). */
  public static final String AGENT = "agent-cache";

  /** Chat session context (TTL 2h). */
  public static final String SESSION = "session-cache";

  private CacheNames() {
  }
}
