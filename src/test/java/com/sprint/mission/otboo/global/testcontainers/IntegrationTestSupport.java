package com.sprint.mission.otboo.global.testcontainers;

/**
 * 전체 컨텍스트를 띄우는 통합 테스트의 공통 베이스.
 *
 * <p>{@code @SpringBootTest}는 ES 커넥션을 포함한 전체 빈을 초기화하므로, ES를 직접 쓰지 않는
 * 테스트도 컨테이너가 필요하다.
 *
 * <p>Kafka는 여기 공용으로 얹지 않는다 — 이 클래스를 상속하는 모든 테스트가 필요 여부와 무관하게
 * 임베디드 브로커를 띄우게 되므로, Kafka 발행/소비를 실제로 검증해야 하는 테스트 클래스만
 * {@code @EmbeddedKafka}를 그 클래스에 직접 선언한다. 메시지 발행 전에는 반드시
 * {@code ContainerTestUtils.waitForAssignment(...)}로 컨슈머 그룹의 파티션 할당(리밸런스)이
 * 끝났는지 확인한다 — 끝나기 전에 발행하면 {@code auto.offset.reset=latest} 기본값 탓에 메시지가
 * 조용히 스킵된다.
 *
 * <p>ES만 필요한 슬라이스 테스트({@code @DataElasticsearchTest})는
 * {@link ElasticsearchTestContainerSupport}를 직접 상속한다.
 */
public abstract class IntegrationTestSupport extends ElasticsearchTestContainerSupport {

}
