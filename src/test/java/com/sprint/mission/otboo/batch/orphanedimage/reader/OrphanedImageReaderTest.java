package com.sprint.mission.otboo.batch.orphanedimage.reader;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import com.sprint.mission.otboo.batch.orphanedimage.OrphanedImageFinder;
import com.sprint.mission.otboo.batch.orphanedimage.metrics.OrphanedImageCleanupMetrics;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class OrphanedImageReaderTest {

  @Mock
  private OrphanedImageFinder finder;
  @Mock
  private OrphanedImageCleanupMetrics metrics;
  private OrphanedImageReader reader;

  @BeforeEach
  void setUp() {
    reader = new OrphanedImageReader(finder, metrics);
  }

  @Nested
  @DisplayName("키 조회")
  class Read {

    @Test
    @DisplayName("계산된_유실_키를_하나씩_순서대로_반환하고_끝나면_null을_반환한다")
    void 계산된_유실_키를_하나씩_순서대로_반환하고_끝나면_null을_반환한다() {
      // given
      given(finder.find()).willReturn(
          new OrphanedImageFinder.Result(List.of("profile/a.png", "profile/b.png"), false));

      // when & then
      assertThat(reader.read()).isEqualTo("profile/a.png");
      assertThat(reader.read()).isEqualTo("profile/b.png");
      assertThat(reader.read()).isNull();
      verify(finder, times(1)).find(); // find()는 최초 1회만 호출
      verify(metrics, never()).countCapped();
    }

    @Test
    @DisplayName("안전_상한에_걸리면_아이템_없이_바로_null을_반환하고_countCapped를_계측한다")
    void 안전_상한에_걸리면_아이템_없이_바로_null을_반환하고_countCapped를_계측한다() {
      // given
      given(finder.find()).willReturn(new OrphanedImageFinder.Result(List.of(), true));

      // when & then
      assertThat(reader.read()).isNull();
      verify(metrics).countCapped();
    }
  }
}
