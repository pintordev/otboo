package com.sprint.mission.otboo.batch.logbackup.reader;

import com.sprint.mission.otboo.batch.logbackup.config.LogBackupProperties;
import com.sprint.mission.otboo.batch.logbackup.config.LogBackupProperties.LogGroupTarget;
import com.sprint.mission.otboo.batch.logbackup.dto.LogContent;
import com.sprint.mission.otboo.batch.logbackup.exception.LogBackupReadFailedException;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.List;
import lombok.extern.slf4j.Slf4j;
import org.springframework.batch.core.configuration.annotation.StepScope;
import org.springframework.batch.infrastructure.item.ItemReader;
import org.springframework.stereotype.Component;
import software.amazon.awssdk.core.exception.SdkException;
import software.amazon.awssdk.services.cloudwatchlogs.CloudWatchLogsClient;
import software.amazon.awssdk.services.cloudwatchlogs.model.FilterLogEventsRequest;
import software.amazon.awssdk.services.cloudwatchlogs.model.FilterLogEventsResponse;

@Slf4j
@StepScope
@Component
public class LogBackupReader implements ItemReader<LogContent> {

  private record Target(LogGroupTarget group, LocalDate date) {

  }

  private final CloudWatchLogsClient cloudWatchLogsClient;
  private final List<LogGroupTarget> logGroups;
  private final int lookbackDays;

  private Deque<Target> pendingTargets;
  private Target currentTarget;
  private long startTime;
  private long endTime;
  private String nextToken;
  private int pageNumber;
  private boolean currentTargetDone = true;

  public LogBackupReader(CloudWatchLogsClient cloudWatchLogsClient,
      LogBackupProperties logBackupProperties) {
    this.cloudWatchLogsClient = cloudWatchLogsClient;
    this.logGroups = logBackupProperties.logGroups();
    this.lookbackDays = logBackupProperties.lookbackDays();
  }

  @Override
  public LogContent read() {
    if (pendingTargets == null) {
      LocalDate yesterday = LocalDate.now(ZoneOffset.UTC).minusDays(1);
      pendingTargets = new ArrayDeque<>();
      for (LogGroupTarget group : logGroups) {
        for (int i = lookbackDays - 1; i >= 0; i--) {
          pendingTargets.addLast(new Target(group, yesterday.minusDays(i)));
        }
      }
      log.info("LogBackupReader 시작: logGroups={}, lookbackDays={}, 대상={}",
          logGroups, lookbackDays, pendingTargets);
    }

    while (true) {
      if (currentTargetDone) {
        if (pendingTargets.isEmpty()) {
          return null;
        }
        currentTarget = pendingTargets.pollFirst();
        LocalDate date = currentTarget.date();
        startTime = date.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli();
        endTime = date.plusDays(1).atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli() - 1;
        nextToken = null;
        pageNumber = 0;
        currentTargetDone = false;
      }

      LogGroupTarget group = currentTarget.group();
      FilterLogEventsRequest.Builder requestBuilder = FilterLogEventsRequest.builder()
          .logGroupName(group.name())
          .logStreamNamePrefix(group.streamPrefix())
          .startTime(startTime)
          .endTime(endTime);
      if (nextToken != null) {
        requestBuilder.nextToken(nextToken);
      }

      FilterLogEventsResponse response;
      try {
        response = cloudWatchLogsClient.filterLogEvents(requestBuilder.build());
      } catch (SdkException e) {
        throw LogBackupReadFailedException.wrap(e);
      }
      List<String> lines = response.events().stream().map(e -> e.message()).toList();

      nextToken = response.nextToken();
      if (nextToken == null) {
        currentTargetDone = true;
      }

      if (!lines.isEmpty()) {
        pageNumber++;
        byte[] content = String.join("\n", lines).getBytes(StandardCharsets.UTF_8);
        return new LogContent(group.streamPrefix(), currentTarget.date(), content, pageNumber);
      }
    }
  }
}
