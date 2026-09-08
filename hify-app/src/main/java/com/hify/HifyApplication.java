package com.hify;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Entry point of the Hify modular monolith.
 *
 * <p>Deliberately lives in the root {@code com.hify} package so that component
 * scanning reaches every module (com.hify.common, com.hify.provider, ...) —
 * GlobalExceptionHandler, MybatisPlusConfig, RedisConfig and the module
 * mappers all rely on this.
 *
 * <p>Mapper scanning: every module keeps its mappers in a {@code *.mapper}
 * sub-package below com.hify, so one recursive scan of the root package
 * covers them all. Note that values like {@code com.hify.**.mapper} do NOT
 * work here — the scanner treats them as literal package paths.
 *
 * <p>{@code markerInterface = BaseMapper.class} restricts the scan to real
 * mappers: without it every interface below com.hify (service interfaces
 * included) is registered as a Mapper bean, which shadows the @Service
 * implementation during by-type injection and ends in
 * "Invalid bound statement (not found): <Service>.method".
 */
@SpringBootApplication
@MapperScan(basePackages = "com.hify", markerInterface = BaseMapper.class)
public class HifyApplication {

  public static void main(String[] args) {
    SpringApplication.run(HifyApplication.class, args);
  }
}
