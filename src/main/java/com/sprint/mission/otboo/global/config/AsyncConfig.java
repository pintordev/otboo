package com.sprint.mission.otboo.global.config;

import java.util.concurrent.Executor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.aop.interceptor.AsyncUncaughtExceptionHandler;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.AsyncConfigurer;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

@Slf4j
@Configuration
@EnableAsync
public class AsyncConfig implements AsyncConfigurer {

  @Bean(name = "mailExecutor")
  public Executor mailExecutor() {
    // TODO: 현재 아래 설정은 임시 값. 팀 논의 필요 지점
    ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
    executor.setCorePoolSize(2);
    executor.setMaxPoolSize(4);
    executor.setQueueCapacity(50);
    executor.setThreadNamePrefix("mail-async-");
    executor.initialize();
    // TODO: 유실에 대한 정책 정의 논의 필요
    return executor;
  }

  @Bean(name = "notificationExecutor")
  public Executor notificationExecutor() {
    // TODO: 현재 아래 설정은 임시 값. 팀 논의 필요 지점
    ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
    executor.setCorePoolSize(2);
    executor.setMaxPoolSize(4);
    executor.setQueueCapacity(50);
    executor.setThreadNamePrefix("notification-async-");
    executor.initialize();
    return executor;
  }

  @Override
  public AsyncUncaughtExceptionHandler getAsyncUncaughtExceptionHandler() {
    return (throwable, method, params) -> {
      log.error("비동기 작업 실패: method={}, params={}", method.getName(), params, throwable);
      // 추후 필요시 알람/모니터링 시스템에 통보
    };
  }
}
