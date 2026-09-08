package com.hify.common.config;

import com.baomidou.mybatisplus.annotation.DbType;
import com.baomidou.mybatisplus.extension.plugins.MybatisPlusInterceptor;
import com.baomidou.mybatisplus.extension.plugins.inner.PaginationInnerInterceptor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * MyBatis-Plus infrastructure shared by every business module.
 *
 * <p>Mapper scanning lives on the startup class ({@code @MapperScan("com.hify")}
 * in HifyApplication) — keep it in exactly one place to avoid duplicate bean
 * definitions. Logical delete / auto-fill are not expressible here: they live
 * in {@link com.hify.common.entity.BaseEntity} (fields annotated
 * {@code @TableLogic} / {@code fill}) and the {@link MyMetaObjectHandler}
 * filling them.
 *
 * <p>Like GlobalExceptionHandler, these beans only take effect when the app
 * component-scans the {@code com.hify} package tree.
 */
@Configuration
public class MybatisPlusConfig {

  /** Pagination support (MySQL dialect, see CLAUDE.md page/pageSize convention). */
  @Bean
  public MybatisPlusInterceptor mybatisPlusInterceptor() {
    MybatisPlusInterceptor interceptor = new MybatisPlusInterceptor();
    interceptor.addInnerInterceptor(new PaginationInnerInterceptor(DbType.MYSQL));
    return interceptor;
  }
}
