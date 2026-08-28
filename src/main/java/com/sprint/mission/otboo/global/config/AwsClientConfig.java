package com.sprint.mission.otboo.global.config;

import com.sprint.mission.otboo.global.file.properties.FileProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.cloudwatchlogs.CloudWatchLogsClient;
import software.amazon.awssdk.services.s3.S3Client;

@Configuration
public class AwsClientConfig {

  // otboo.file.impl과 무관하게 항상 노출한다 — S3Client.builder().build()는 생성 시점에
  // 네트워크 호출을 하지 않아 impl=local에서도 안전하고, S3를 직접 쓰는 배치(유실 이미지 정리,
  // 로그 백업 등)가 별도 빈 없이 이 하나를 그대로 주입받을 수 있다.
  @Bean
  public S3Client s3Client(FileProperties fileProperties) {
    return S3Client.builder().region(Region.of(fileProperties.s3().region())).build();
  }

  @Bean
  public CloudWatchLogsClient cloudWatchLogsClient(FileProperties fileProperties) {
    return CloudWatchLogsClient.builder().region(Region.of(fileProperties.s3().region())).build();
  }
}
