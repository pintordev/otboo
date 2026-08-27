package com.sprint.mission.otboo.batch.orphanedimage.reader;

import com.sprint.mission.otboo.batch.orphanedimage.OrphanedImageFinder;
import com.sprint.mission.otboo.batch.orphanedimage.metrics.OrphanedImageCleanupMetrics;
import java.util.Iterator;
import lombok.RequiredArgsConstructor;
import org.springframework.batch.core.configuration.annotation.StepScope;
import org.springframework.batch.infrastructure.item.ItemReader;
import org.springframework.stereotype.Component;

@StepScope
@Component
@RequiredArgsConstructor
public class OrphanedImageReader implements ItemReader<String> {

  private final OrphanedImageFinder finder;
  private final OrphanedImageCleanupMetrics metrics;

  private Iterator<String> iterator;

  @Override
  public String read() {
    if (iterator == null) {
      OrphanedImageFinder.Result result = finder.find();
      if (result.capped()) {
        metrics.countCapped();
      }
      iterator = result.orphanedKeys().iterator();
    }
    return iterator.hasNext() ? iterator.next() : null;
  }
}
