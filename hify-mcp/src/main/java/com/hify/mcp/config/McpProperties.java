package com.hify.mcp.config;

import java.time.Duration;
import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Getter
@Setter
@Component
@ConfigurationProperties(prefix = "hify.mcp")
public class McpProperties {

  private Duration connectTimeout = Duration.ofSeconds(10);
  private Duration requestTimeout = Duration.ofSeconds(60);
}
