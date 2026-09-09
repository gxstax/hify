package com.hify.common.log;

import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * Registers {@link RequestLogInterceptor} for every path.
 * (Exclude noisy endpoints here if needed, e.g. health probes.)
 */
@Configuration
@RequiredArgsConstructor
public class RequestLogConfig implements WebMvcConfigurer {

  private final RequestLogInterceptor requestLogInterceptor;

  @Override
  public void addInterceptors(InterceptorRegistry registry) {
    registry.addInterceptor(requestLogInterceptor).addPathPatterns("/**");
  }
}
