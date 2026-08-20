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
    executor.setTaskDecorator(new MdcTaskDecorator());
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
    executor.setTaskDecorator(new MdcTaskDecorator());
    executor.initialize();
    return executor;
  }

  @Bean(name = "sseDisconnectExecutor")
  public Executor sseDisconnectExecutor() {
    // TODO: 현재 아래 설정은 임시 값. 팀 논의 필요 지점
    // notificationExecutor와 분리 — 로그인발 SSE 정리가 실제 알림 전송과 풀을 다투지 않도록 함
    ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
    executor.setCorePoolSize(2);
    executor.setMaxPoolSize(4);
    executor.setQueueCapacity(50);
    executor.setThreadNamePrefix("sse-disconnect-async-");
    executor.setTaskDecorator(new MdcTaskDecorator());
    executor.initialize();
    return executor;
  }

  @Bean(name = "sseListenerExecutor")
  public Executor sseListenerExecutor() {
    // TODO: 현재 아래 설정은 임시 값. 팀 논의 필요 지점
    // RedisMessageListenerContainer 기본값(SimpleAsyncTaskExecutor, 호출마다 새 스레드)을
    // 바운드 풀로 교체 — 구독 콜백에서 emitter IO가 지연돼도 무제한으로 스레드가 늘지 않게 함
    ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
    executor.setCorePoolSize(4);
    executor.setMaxPoolSize(4);
    executor.setQueueCapacity(100);
    executor.setThreadNamePrefix("sse-listener-");
    executor.setTaskDecorator(new MdcTaskDecorator());
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
