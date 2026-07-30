package com.sprint.mission.otboo.domain.authuser.auth.event;

public record TempPasswordRequestedEvent(
    String email,
    String rawTempPassword
) {

  @Override
  public String toString() {
    return email;
  }
}
