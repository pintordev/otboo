package com.sprint.mission.otboo.global.config;

import javax.sql.DataSource;
import org.springframework.batch.core.repository.JobRepository;
import org.springframework.batch.core.repository.support.JdbcJobRepositoryFactoryBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.transaction.PlatformTransactionManager;

@Configuration
public class BatchJobRepositoryConfig {

  @Bean(name = "jdbcJobRepository")
  @Primary
  public JobRepository jobRepository(
      DataSource dataSource, PlatformTransactionManager transactionManager) throws Exception {
    JdbcJobRepositoryFactoryBean factory = new JdbcJobRepositoryFactoryBean();
    factory.setDataSource(dataSource);
    factory.setTransactionManager(transactionManager);
    factory.afterPropertiesSet();
    return factory.getObject();
  }
}
