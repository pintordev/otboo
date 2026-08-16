package com.sprint.mission.otboo.batch.weatherfetch.service;

import com.sprint.mission.otboo.batch.weatherfetch.config.WeatherChangeProperties;
import com.sprint.mission.otboo.domain.weathernotification.weather.entity.WeatherGrid;
import com.sprint.mission.otboo.domain.weathernotification.weather.repository.WeatherRepository;
import com.sprint.mission.otboo.external.kma.KmaBaseTimeCalculator.BaseTime;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

// WeatherFetchJobListener.afterJob()에서 호출된다 - 감지·발행 로직 자체는
// WeatherSuddenChangeChunkProcessor로 옮기고, 여기서는 gridChunkSize만큼 잘라 청크별
// 트랜잭션으로 위임만 한다(#163, PR #131 리뷰 - 전체 격자 단일 트랜잭션 리스크). Step으로
// 쪼개는 대신 afterJob을 유지한 이유는 작업 문서 참고 - Step 체이닝은 Job 최종 상태가
// 마지막 실행 Step의 exit status를 따라가 감지 실패가 Job 실패로 잘못 반영될 위험이 있다.
@Slf4j
@RequiredArgsConstructor
@Component
public class WeatherSuddenChangeNotifier {

  private static final String EVENING_BASE_TIME = "2000";
  private static final String LAST_BASE_TIME = "2300";
  private static final DateTimeFormatter BASE_DATE_FORMATTER =
      DateTimeFormatter.ofPattern("yyyyMMdd");

  private final WeatherRepository weatherRepository;
  private final WeatherSuddenChangeChunkProcessor chunkProcessor;
  private final WeatherChangeProperties weatherChangeProperties;

  public void detectAndNotify(BaseTime baseTime) {
    List<WeatherGrid> updatedGrids = weatherRepository.findGridsUpdatedAt(baseTime.toInstant());
    if (updatedGrids.isEmpty()) {
      log.warn("날씨 급변 감지 대상 격자 0건: baseTime={}", baseTime);
      return;
    }

    boolean shouldHandleD0 = !baseTime.baseTime().equals(LAST_BASE_TIME);
    boolean shouldHandleD1 = baseTime.baseTime().equals(EVENING_BASE_TIME);
    // 배치가 지연되어 자정을 넘겨 실행돼도 baseTime이 가리키는 날짜를 써야 한다 - 시스템
    // 시각(Clock)을 쓰면 지연 실행 시 today가 하루 밀릴 수 있다.
    LocalDate today = LocalDate.parse(baseTime.baseDate(), BASE_DATE_FORMATTER);

    int d0Notified = 0;
    int d1Notified = 0;
    int chunkSize = weatherChangeProperties.gridChunkSize();
    for (int from = 0; from < updatedGrids.size(); from += chunkSize) {
      int to = Math.min(from + chunkSize, updatedGrids.size());
      List<WeatherGrid> chunk = updatedGrids.subList(from, to);
      WeatherSuddenChangeChunkProcessor.ChunkResult result =
          chunkProcessor.process(chunk, baseTime, today, shouldHandleD0, shouldHandleD1);
      d0Notified += result.d0Notified();
      d1Notified += result.d1Notified();
    }

    log.info("날씨 급변 감지 완료: 평가 격자 수={}, D0 알림={}, D1 알림={}", updatedGrids.size(),
        d0Notified, d1Notified);
  }
}