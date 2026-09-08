package com.hify.common.config;

import com.hify.common.constant.CacheNames;
import java.time.Duration;
import java.util.HashMap;
import java.util.Map;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.cache.RedisCacheConfiguration;
import org.springframework.data.redis.cache.RedisCacheManager;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.serializer.GenericJackson2JsonRedisSerializer;
import org.springframework.data.redis.serializer.RedisSerializationContext;
import org.springframework.data.redis.serializer.StringRedisSerializer;

/**
 * Spring Cache on Redis. Key scheme: {@code hify:{cacheName}::{key}} with
 * String keys and JSON values (same convention as {@link RedisConfig}, so
 * cached entries are human-readable in redis-cli).
 *
 * <p>TTLs per CLAUDE.md cache strategy: provider / agent configs 30min,
 * chat session context 2h. Anything cached without a dedicated name falls
 * back to the 30min default.
 */
@Configuration
@EnableCaching
public class CacheConfig {

  private static final Duration TTL_DEFAULT = Duration.ofMinutes(30);
  private static final Duration TTL_SESSION = Duration.ofHours(2);

  @Bean
  public RedisCacheManager cacheManager(RedisConnectionFactory connectionFactory) {
    RedisCacheConfiguration baseConfig = RedisCacheConfiguration.defaultCacheConfig()
        .entryTtl(TTL_DEFAULT)
        .prefixCacheNameWith("hify:")
        .serializeKeysWith(
            RedisSerializationContext.SerializationPair.fromSerializer(new StringRedisSerializer()))
        .serializeValuesWith(
            RedisSerializationContext.SerializationPair.fromSerializer(new GenericJackson2JsonRedisSerializer()));

    Map<String, RedisCacheConfiguration> cacheConfigs = new HashMap<>();
    cacheConfigs.put(CacheNames.PROVIDER, baseConfig.entryTtl(TTL_DEFAULT));
    cacheConfigs.put(CacheNames.AGENT, baseConfig.entryTtl(TTL_DEFAULT));
    cacheConfigs.put(CacheNames.SESSION, baseConfig.entryTtl(TTL_SESSION));

    return RedisCacheManager.builder(connectionFactory)
        .cacheDefaults(baseConfig)
        .withInitialCacheConfigurations(cacheConfigs)
        .build();
  }
}
