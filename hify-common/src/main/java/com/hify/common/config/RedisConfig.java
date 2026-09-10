package com.hify.common.config;

import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.fasterxml.jackson.datatype.jsr310.deser.LocalDateTimeDeserializer;
import com.fasterxml.jackson.datatype.jsr310.ser.LocalDateTimeSerializer;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.serializer.GenericJackson2JsonRedisSerializer;
import org.springframework.data.redis.serializer.StringRedisSerializer;

/**
 * Redis serialization convention used by every module:
 * String keys (readable in redis-cli), JSON values carrying type info
 * (GenericJackson2JsonRedisSerializer embeds {@code @class}, so stored
 * POJOs deserialize back to their original type without manual mapping).
 *
 * <p>Replaces Boot's default {@code RedisTemplate<Object, Object>} bean
 * (same name), which would otherwise serialize keys/values as JdkSerialization.
 */
@Configuration
public class RedisConfig {

  /**
   * Shared value serializer for both the RedisTemplate and the cache manager
   * (see CacheConfig) — one convention, one instance.
   *
   * <p>Crucially it carries a JavaTimeModule: the plain
   * {@code new GenericJackson2JsonRedisSerializer()} builds an ObjectMapper
   * WITHOUT Java 8 time support, so caching any entity with LocalDateTime
   * fields (e.g. Provider.createdAt) fails with InvalidDefinitionException.
   * The datetime format mirrors the global MVC convention (JacksonConfig).
   */
  public static GenericJackson2JsonRedisSerializer redisJsonSerializer() {
    JavaTimeModule javaTimeModule = new JavaTimeModule();
    javaTimeModule.addSerializer(
        java.time.LocalDateTime.class, new LocalDateTimeSerializer(JacksonConfig.DATE_TIME_FORMAT));
    javaTimeModule.addDeserializer(
        java.time.LocalDateTime.class, new LocalDateTimeDeserializer(JacksonConfig.DATE_TIME_FORMAT));

    ObjectMapper mapper = new ObjectMapper();
    mapper.registerModule(javaTimeModule);
    mapper.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
    // GenericJackson2JsonRedisSerializer activates default typing on the
    // mapper it receives, so @class metadata is preserved for deserialization.
    return new GenericJackson2JsonRedisSerializer(mapper);
  }

  @Bean
  public RedisTemplate<String, Object> redisTemplate(RedisConnectionFactory connectionFactory) {
    RedisTemplate<String, Object> template = new RedisTemplate<>();
    template.setConnectionFactory(connectionFactory);

    StringRedisSerializer keySerializer = new StringRedisSerializer();
    GenericJackson2JsonRedisSerializer valueSerializer = redisJsonSerializer();

    template.setKeySerializer(keySerializer);
    template.setHashKeySerializer(keySerializer);
    template.setValueSerializer(valueSerializer);
    template.setHashValueSerializer(valueSerializer);

    template.afterPropertiesSet();
    return template;
  }
}
