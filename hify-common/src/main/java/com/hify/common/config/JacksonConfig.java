package com.hify.common.config;

import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.deser.LocalDateDeserializer;
import com.fasterxml.jackson.datatype.jsr310.deser.LocalDateTimeDeserializer;
import com.fasterxml.jackson.datatype.jsr310.ser.LocalDateSerializer;
import com.fasterxml.jackson.datatype.jsr310.ser.LocalDateTimeSerializer;
import java.time.format.DateTimeFormatter;
import org.springframework.boot.autoconfigure.jackson.Jackson2ObjectMapperBuilderCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Global Jackson temporal formats. JavaTimeModule itself is registered by
 * Spring Boot auto-configuration (jsr310 is on the classpath); this customizer
 * disables numeric timestamps and pins the wire format for both directions:
 *
 * <ul>
 *   <li>{@code LocalDateTime} -> {@code yyyy-MM-dd'T'HH:mm:ss} (ISO 8601, seconds always present)</li>
 *   <li>{@code LocalDate}     -> {@code yyyy-MM-dd}</li>
 * </ul>
 *
 * <p>Note: request bodies must match these formats exactly (no millis) —
 * the app controls both sides, keep it strict.
 */
@Configuration
public class JacksonConfig {

  public static final DateTimeFormatter DATE_TIME_FORMAT =
      DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss");

  public static final DateTimeFormatter DATE_FORMAT =
      DateTimeFormatter.ofPattern("yyyy-MM-dd");

  @Bean
  public Jackson2ObjectMapperBuilderCustomizer jacksonTemporalCustomizer() {
    return builder -> builder
        .featuresToDisable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
        .serializers(
            new LocalDateTimeSerializer(DATE_TIME_FORMAT),
            new LocalDateSerializer(DATE_FORMAT))
        .deserializers(
            new LocalDateTimeDeserializer(DATE_TIME_FORMAT),
            new LocalDateDeserializer(DATE_FORMAT));
  }
}
