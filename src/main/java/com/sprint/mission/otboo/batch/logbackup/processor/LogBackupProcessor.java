package com.sprint.mission.otboo.batch.logbackup.processor;

import com.sprint.mission.otboo.batch.logbackup.dto.LogContent;
import com.sprint.mission.otboo.batch.logbackup.dto.UploadPayload;
import java.io.ByteArrayOutputStream;
import java.time.format.DateTimeFormatter;
import java.util.zip.GZIPOutputStream;
import org.springframework.batch.infrastructure.item.ItemProcessor;
import org.springframework.stereotype.Component;

@Component
public class LogBackupProcessor implements ItemProcessor<LogContent, UploadPayload> {

  private static final DateTimeFormatter PATH_FORMATTER = DateTimeFormatter.ofPattern("yyyy/MM/dd");
  private static final DateTimeFormatter FILE_FORMATTER = DateTimeFormatter.ofPattern("yyyyMMdd");

  @Override
  public UploadPayload process(LogContent item) throws Exception {
    String s3Key = "logs/" + item.groupLabel() + "/" + item.date().format(PATH_FORMATTER)
        + "/" + item.groupLabel() + "-" + item.date().format(FILE_FORMATTER)
        + String.format("-%03d", item.pageNumber()) + ".log.gz";
    return new UploadPayload(s3Key, gzip(item.lines()));
  }

  private byte[] gzip(byte[] raw) throws Exception {
    ByteArrayOutputStream out = new ByteArrayOutputStream();
    try (GZIPOutputStream gzipOut = new GZIPOutputStream(out)) {
      gzipOut.write(raw);
    }
    return out.toByteArray();
  }
}
