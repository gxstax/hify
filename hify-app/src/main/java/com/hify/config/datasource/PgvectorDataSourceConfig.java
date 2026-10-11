package com.hify.config.datasource;

import com.zaxxer.hikari.HikariDataSource;
import javax.sql.DataSource;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.jdbc.DataSourceProperties;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.transaction.PlatformTransactionManager;

/** Configures the dedicated PostgreSQL database used only for pgvector chunks. */
@Configuration
public class PgvectorDataSourceConfig {

  @Bean
  @ConfigurationProperties("spring.datasource.pgvector")
  public DataSourceProperties pgvectorDataSourceProperties() {
    return new DataSourceProperties();
  }

  @Bean
  @ConfigurationProperties("spring.datasource.pgvector.hikari")
  public HikariDataSource pgvectorDataSource(
      @Qualifier("pgvectorDataSourceProperties") DataSourceProperties properties) {
    return properties.initializeDataSourceBuilder().type(HikariDataSource.class).build();
  }

  @Bean
  public JdbcTemplate pgvectorJdbcTemplate(
      @Qualifier("pgvectorDataSource") DataSource dataSource) {
    return new JdbcTemplate(dataSource);
  }

  @Bean
  public PlatformTransactionManager pgvectorTransactionManager(
      @Qualifier("pgvectorDataSource") DataSource dataSource) {
    return new DataSourceTransactionManager(dataSource);
  }
}
