package com.hify;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Entry point of the Hify modular monolith.
 *
 * <p>Deliberately lives in the root {@code com.hify} package so that component
 * scanning reaches every module (com.hify.common, com.hify.provider, ...) —
 * GlobalExceptionHandler, MybatisPlusConfig, RedisConfig and the module
 * mappers all rely on this.
 */
@SpringBootApplication
public class HifyApplication {

  public static void main(String[] args) {
    SpringApplication.run(HifyApplication.class, args);
  }
}
