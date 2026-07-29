package com.sprint.mission.otboo.global.batch;

import lombok.extern.slf4j.Slf4j;
import org.springframework.batch.core.listener.SkipListener;
import org.springframework.stereotype.Component;

@Slf4j
@Component
public class SkipLoggingListener implements SkipListener<Object, Object> {

  @Override
  public void onSkipInRead(Throwable t) {
    log.error("배치 Reader skip", t);
  }

  @Override
  public void onSkipInProcess(Object item, Throwable t) {
    log.error("배치 Processor skip | item={}", item, t);
  }

  @Override
  public void onSkipInWrite(Object item, Throwable t) {
    log.error("배치 Writer skip | item={}", item, t);
  }
}