package com.sprint.mission.otboo.global.config;

import java.util.concurrent.Executor;
import java.util.concurrent.ThreadPoolExecutor;
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

  @Bean(name = "sseDisconnectExecutor")
  public Executor sseDisconnectExecutor() {
    // TODO: 현재 아래 설정은 임시 값. 팀 논의 필요 지점
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
    // 큐가 차면 기본 AbortPolicy가 태스크를 버려 SSE 알림이 조용히 유실된다(dmListenerExecutor와
    // 동일 이유). Redis 구독 스레드에서 직접 실행해 백프레셔를 건다.
    executor.setRejectedExecutionHandler(new ThreadPoolExecutor.CallerRunsPolicy());
    executor.initialize();
    return executor;
  }

  @Bean(name = "dmListenerExecutor")
  public Executor dmListenerExecutor() {
    // TODO: 현재 아래 설정은 임시 값. 팀 논의 필요 지점
    // sseListenerExecutor와 분리 — SSE 구독 콜백 지연이 DM 전파를 막지 않게 함
    ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
    executor.setCorePoolSize(4);
    executor.setMaxPoolSize(4);
    executor.setQueueCapacity(100);
    executor.setThreadNamePrefix("dm-listener-");
    executor.setTaskDecorator(new MdcTaskDecorator());
    // 큐가 차면 기본 AbortPolicy가 태스크를 버려 DM이 조용히 유실된다.
    // Redis 구독 스레드에서 직접 실행해 백프레셔를 건다.
    executor.setRejectedExecutionHandler(new ThreadPoolExecutor.CallerRunsPolicy());
    executor.initialize();
    return executor;
  }

  @Bean(name = "feedIndexExecutor")
  public Executor feedIndexExecutor() {
    // TODO: 현재 아래 설정은 임시 값. 팀 논의 필요 지점
    ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
    executor.setCorePoolSize(2);
    executor.setMaxPoolSize(4);
    executor.setQueueCapacity(100);
    executor.setThreadNamePrefix("feed-index-async-");
    executor.setTaskDecorator(new MdcTaskDecorator());
    // 큐가 차면 기본 AbortPolicy가 태스크를 버려 색인이 조용히 유실된다.
    // 요청 스레드에서 직접 실행해 백프레셔를 건다 — 이 경우 응답이 느려질 뿐 인덱스는 맞는다.
    executor.setRejectedExecutionHandler(new ThreadPoolExecutor.CallerRunsPolicy());
    executor.initialize();
    return executor;
  }

  @Bean(name = "singleFlightListenerExecutor")
  public Executor singleFlightListenerExecutor() {
    // TODO: 현재 아래 설정은 임시 값. 팀 논의 필요 지점
    // sseListenerExecutor와 분리 — single-flight 완료(done/failed) 메시지는 비리더 요청이
    // 그 자리에서 기다리는 지연 민감 신호라, SSE emitter IO 지연이 이 풀까지 잠식하면 안 된다.
    ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
    executor.setCorePoolSize(2);
    executor.setMaxPoolSize(4);
    executor.setQueueCapacity(100);
    executor.setThreadNamePrefix("single-flight-listener-");
    executor.setTaskDecorator(new MdcTaskDecorator());
    executor.initialize();
    return executor;
  }

  @Bean(name = "weatherRefreshExecutor")
  public Executor weatherRefreshExecutor() {
    // TODO: 현재 아래 설정은 임시 값. 팀 논의 필요 지점
    ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
    executor.setCorePoolSize(4);
    executor.setMaxPoolSize(8);
    executor.setQueueCapacity(50);
    executor.setThreadNamePrefix("weather-refresh-");
    executor.setTaskDecorator(new MdcTaskDecorator());
    // 포화 시 요청(서블릿) 스레드에서 외부 호출을 대신 실행하지 않는다 - 거부 예외는
    // WeatherService의 폴백 경로에서 처리한다.
    executor.setRejectedExecutionHandler(new ThreadPoolExecutor.AbortPolicy());
    executor.initialize();
    return executor;
  }

  @Bean(name = "kakaoLocationExecutor")
  public Executor kakaoLocationExecutor() {
    // TODO: 현재 아래 설정은 임시 값. 팀 논의 필요 지점
    // weatherRefreshExecutor와 분리 - 기상청/카카오는 서로 다른 외부 시스템(응답 속도·쿼터·장애
    // 패턴이 다름)이라 한쪽이 느려지거나 막혀도 다른 쪽 처리 능력까지 잠식되지 않게 한다
    ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
    executor.setCorePoolSize(4);
    executor.setMaxPoolSize(8);
    executor.setQueueCapacity(50);
    executor.setThreadNamePrefix("kakao-location-");
    executor.setTaskDecorator(new MdcTaskDecorator());
    // 포화 시 요청(서블릿) 스레드에서 외부 호출을 대신 실행하지 않는다 - 거부 예외는
    // WeatherService의 폴백 경로에서 처리한다.
    executor.setRejectedExecutionHandler(new ThreadPoolExecutor.AbortPolicy());
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
