package com.hify.config.datasource;

import com.baomidou.mybatisplus.autoconfigure.MybatisPlusProperties;
import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.config.GlobalConfig;
import com.baomidou.mybatisplus.core.handlers.MetaObjectHandler;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.extension.plugins.MybatisPlusInterceptor;
import com.baomidou.mybatisplus.spring.MybatisSqlSessionFactoryBean;
import com.zaxxer.hikari.HikariDataSource;
import javax.sql.DataSource;
import org.apache.ibatis.session.SqlSessionFactory;
import org.mybatis.spring.SqlSessionTemplate;
import org.mybatis.spring.annotation.MapperScan;
import com.baomidou.mybatisplus.autoconfigure.SpringBootVFS;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.jdbc.DataSourceProperties;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.util.StringUtils;

/** Configures MySQL as the primary database for every MyBatis-Plus mapper. */
@Configuration
@EnableConfigurationProperties(MybatisPlusProperties.class)
@MapperScan(
    basePackages = "com.hify",
    markerInterface = BaseMapper.class,
    sqlSessionFactoryRef = "mysqlSqlSessionFactory"
)
public class MysqlDataSourceConfig {

  @Bean
  @Primary
  @ConfigurationProperties("spring.datasource.mysql")
  public DataSourceProperties mysqlDataSourceProperties() {
    return new DataSourceProperties();
  }

  @Bean
  @Primary
  @ConfigurationProperties("spring.datasource.mysql.hikari")
  public HikariDataSource mysqlDataSource(
      @Qualifier("mysqlDataSourceProperties") DataSourceProperties properties) {
    return properties.initializeDataSourceBuilder().type(HikariDataSource.class).build();
  }

  @Bean
  @Primary
  public SqlSessionFactory mysqlSqlSessionFactory(
      @Qualifier("mysqlDataSource") DataSource dataSource,
      MybatisPlusProperties properties,
      MybatisPlusInterceptor interceptor,
      MetaObjectHandler metaObjectHandler) throws Exception {
    MybatisConfiguration configuration = new MybatisConfiguration();
    if (properties.getConfiguration() != null) {
      properties.getConfiguration().applyTo(configuration);
    }

    GlobalConfig globalConfig = properties.getGlobalConfig();
    globalConfig.setMetaObjectHandler(metaObjectHandler);

    MybatisSqlSessionFactoryBean factory = new MybatisSqlSessionFactoryBean();
    factory.setDataSource(dataSource);
    factory.setVfs(SpringBootVFS.class);
    factory.setConfiguration(configuration);
    factory.setGlobalConfig(globalConfig);
    factory.setPlugins(interceptor);
    factory.setMapperLocations(properties.resolveMapperLocations());
    factory.setConfigurationProperties(properties.getConfigurationProperties());

    if (StringUtils.hasText(properties.getTypeAliasesPackage())) {
      factory.setTypeAliasesPackage(properties.getTypeAliasesPackage());
    }
    if (properties.getTypeAliasesSuperType() != null) {
      factory.setTypeAliasesSuperType(properties.getTypeAliasesSuperType());
    }
    if (StringUtils.hasText(properties.getTypeHandlersPackage())) {
      factory.setTypeHandlersPackage(properties.getTypeHandlersPackage());
    }
    if (properties.getDefaultScriptingLanguageDriver() != null) {
      factory.setDefaultScriptingLanguageDriver(properties.getDefaultScriptingLanguageDriver());
    }
    return factory.getObject();
  }

  @Bean
  @Primary
  public SqlSessionTemplate mysqlSqlSessionTemplate(
      @Qualifier("mysqlSqlSessionFactory") SqlSessionFactory sqlSessionFactory) {
    return new SqlSessionTemplate(sqlSessionFactory);
  }

  @Bean
  @Primary
  public PlatformTransactionManager mysqlTransactionManager(
      @Qualifier("mysqlDataSource") DataSource dataSource) {
    return new DataSourceTransactionManager(dataSource);
  }
}
