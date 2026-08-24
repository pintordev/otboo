FROM amazoncorretto:17-alpine AS build
WORKDIR /workspace

COPY gradlew settings.gradle build.gradle ./
COPY gradle gradle
# 의존성만 먼저 받아 레이어 캐싱 — src 변경 시 이 레이어는 그대로 재사용됨(|| true는 캐시 워밍이 실패해도 다음 빌드 단계를 막지 않기 위함)
RUN chmod +x gradlew && ./gradlew --no-daemon dependencies > /dev/null 2>&1 || true

COPY src src
RUN ./gradlew --no-daemon bootJar -x test   # 테스트는 CI가 별도로 돌림 — 이미지 빌드에선 스킵

FROM amazoncorretto:17-alpine
RUN addgroup -S otboo && adduser -S otboo -G otboo   # non-root 실행
WORKDIR /app
COPY --from=build /workspace/build/libs/*-SNAPSHOT.jar app.jar
ADD --checksum=sha256:9d6bc2ad8dd8fb7f730984988e57b8ac0a82d81c7b3b8ae795378718733a509d https://github.com/open-telemetry/opentelemetry-java-instrumentation/releases/download/v2.30.0/opentelemetry-javaagent.jar /app/otel-javaagent.jar
RUN mkdir -p /app/uploads && chown -R otboo:otboo /app   # LocalFileStorageService가 쓰는 업로드 디렉토리

USER otboo
EXPOSE 8080
HEALTHCHECK --interval=30s --timeout=5s --start-period=60s --retries=3 \
  CMD wget --no-verbose --tries=1 --spider http://localhost:8080/actuator/health || exit 1
ENTRYPOINT ["java", "-javaagent:/app/otel-javaagent.jar", "-jar", "app.jar"]