package com.sprint.mission.otboo.global.temppassword.generator;

import java.security.SecureRandom;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

@Component
@Profile({"local", "test"})
public class RandomTempPasswordGenerator implements TempPasswordGenerator {

  private static final int PASSWORD_LENGTH = 12;
  // 혼동되기 쉬운 문자(I, O, l, 0, 1)를 제외해 가독성/오타 방지
  private static final String CHARS = "ABCDEFGHJKLMNPQRSTUVWXYZabcdefghijkmnopqrstuvwxyz23456789!@#$";
  private final SecureRandom random = new SecureRandom();

  @Override
  public String generate() {
    StringBuilder sb = new StringBuilder(12);
    for (int i = 0; i < PASSWORD_LENGTH; i++) {
      sb.append(CHARS.charAt(random.nextInt(CHARS.length())));
    }
    return sb.toString();
  }
}
