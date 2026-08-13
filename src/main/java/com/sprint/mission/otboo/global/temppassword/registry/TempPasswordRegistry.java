package com.sprint.mission.otboo.global.temppassword.registry;

import com.sprint.mission.otboo.global.temppassword.generator.TempPasswordGenerator;
import java.util.UUID;

public interface TempPasswordRegistry {

  TempPasswordGenerator generator();

  default String issue(UUID userId) {
    String rawTempPassword = generator().generate();
    save(userId, rawTempPassword);
    return rawTempPassword;
  }

  void save(UUID userId, String rawTempPassword);

  void revoke(UUID userId);

  boolean matches(UUID userId, String rawPassword);

  int getExpirationMinutes();
}
