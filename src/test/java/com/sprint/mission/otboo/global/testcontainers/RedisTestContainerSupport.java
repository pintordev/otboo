package com.sprint.mission.otboo.global.testcontainers;

import org.testcontainers.containers.GenericContainer;
import org.testcontainers.utility.DockerImageName;

public interface RedisTestContainerSupport {

  GenericContainer<?> REDIS_CONTAINER = createStartedContainer();

  private static GenericContainer<?> createStartedContainer() {
    GenericContainer<?> container =
        new GenericContainer<>(DockerImageName.parse("redis:7-alpine")).withExposedPorts(6379);
    container.start();
    return container;
  }
}