package com.sprint.mission.otboo.global.temppassword.registry;

import java.util.UUID;

public interface TempPasswordRegistry {

  void save(UUID userId, String rawTempPassword);

  void revoke(UUID userId);

  boolean matches(UUID userId, String rawPassword);
}
