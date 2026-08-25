package com.sprint.mission.otboo.domain.weathernotification.notification.event;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import com.navercorp.fixturemonkey.FixtureMonkey;
import com.navercorp.fixturemonkey.api.introspector.ConstructorPropertiesArbitraryIntrospector;
import com.navercorp.fixturemonkey.jakarta.validation.plugin.JakartaValidationPlugin;
import com.sprint.mission.otboo.domain.weathernotification.notification.dto.NotificationDto;
import com.sprint.mission.otboo.domain.weathernotification.notification.kafka.NotificationKafkaTopics;
import com.sprint.mission.otboo.domain.weathernotification.notification.kafka.NotificationOutboxPayload;
import com.sprint.mission.otboo.domain.weathernotification.notification.service.NotificationService;
import com.sprint.mission.otboo.domain.weathernotification.sse.service.SseService;
import com.sprint.mission.otboo.global.event.NotificationLevel;
import com.sprint.mission.otboo.global.event.NotificationRequestedEvent;
import com.sprint.mission.otboo.global.testcontainers.IntegrationTestSupport;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.apache.kafka.clients.admin.NewTopic;
import org.apache.kafka.clients.consumer.Consumer;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.kafka.config.KafkaListenerEndpointRegistry;
import org.springframework.kafka.core.DefaultKafkaConsumerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.listener.MessageListenerContainer;
import org.springframework.kafka.test.EmbeddedKafkaBroker;
import org.springframework.kafka.test.context.EmbeddedKafka;
import org.springframework.kafka.test.utils.ContainerTestUtils;
import org.springframework.kafka.test.utils.KafkaTestUtils;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import tools.jackson.databind.ObjectMapper;

@SpringBootTest
@ActiveProfiles("test")
@EmbeddedKafka(partitions = 1, topics = NotificationKafkaTopics.NOTIFICATION_REQUESTED)
class NotificationRequestedKafkaConsumerTest extends IntegrationTestSupport {

  static final String DLT_TOPIC = NotificationKafkaTopics.NOTIFICATION_REQUESTED + "-dlt";

  private static final FixtureMonkey fixtureMonkey = FixtureMonkey.builder()
      .objectIntrospector(ConstructorPropertiesArbitraryIntrospector.INSTANCE)
      .plugin(new JakartaValidationPlugin())
      .build();

  @Autowired
  private KafkaTemplate<String, String> kafkaTemplate;
  @Autowired
  private ObjectMapper objectMapper;
  @Autowired
  private EmbeddedKafkaBroker embeddedKafkaBroker;
  @Autowired
  private KafkaListenerEndpointRegistry registry;
  @MockitoBean
  private NotificationService notificationService;
  @MockitoBean
  private SseService sseService;

  @BeforeEach
  void waitForConsumerAssignment() {
    // 컨슈머 그룹 리밸런스(파티션 할당)가 끝나기 전에 발행하면 auto.offset.reset=latest 기본값 탓에
    // 메시지가 조용히 스킵된다 — 발행 전에 반드시 할당 완료를 기다린다.
    MessageListenerContainer container = registry.getListenerContainer("notificationRequestedConsumer");
    ContainerTestUtils.waitForAssignment(container, 1);
  }

  @Nested
  @DisplayName("consume")
  class Consume {

    @Test
    @DisplayName("발행된_메시지를_소비해_알림을_생성하고_SSE로_발행한다")
    void 발행된_메시지를_소비해_알림을_생성하고_SSE로_발행한다() {
      // given
      NotificationRequestedEvent event = fixtureMonkey.giveMeBuilder(NotificationRequestedEvent.class)
          .set("receiverIds", Set.of(UUID.randomUUID()))
          .set("title", "제목")
          .set("content", "내용")
          .set("level", NotificationLevel.INFO)
          .sample();
      UUID eventId = UUID.randomUUID();
      List<NotificationDto> notificationDtos = List.of(new NotificationDto(
          UUID.randomUUID(), Instant.now(), event.receiverIds().iterator().next(), "제목", "내용",
          NotificationLevel.INFO));
      given(notificationService.create(eventId, event)).willReturn(notificationDtos);

      // when
      kafkaTemplate.send(NotificationKafkaTopics.NOTIFICATION_REQUESTED,
          objectMapper.writeValueAsString(new NotificationOutboxPayload(eventId, event)));

      // then
      await().atMost(Duration.ofSeconds(5)).untilAsserted(() ->
          verify(sseService).send(notificationDtos, "notifications"));
    }
  }

  @Nested
  @DisplayName("재시도 소진 시 DLT 전달")
  class RetryExhausted {

    @Test
    @DisplayName("컨슈머가_계속_실패하면_재시도_소진_후_DLT_토픽으로_전달된다")
    void 컨슈머가_계속_실패하면_재시도_소진_후_DLT_토픽으로_전달된다() {
      // given
      given(notificationService.create(any(), any())).willThrow(new RuntimeException("강제 실패"));
      NotificationRequestedEvent event = fixtureMonkey.giveMeBuilder(NotificationRequestedEvent.class)
          .set("receiverIds", Set.of(UUID.randomUUID()))
          .set("title", "제목")
          .set("content", "내용")
          .set("level", NotificationLevel.INFO)
          .sample();
      String payload = objectMapper.writeValueAsString(
          new NotificationOutboxPayload(UUID.randomUUID(), event));

      embeddedKafkaBroker.addTopics(new NewTopic(DLT_TOPIC, 1, (short) 1));
      Map<String, Object> consumerProps =
          KafkaTestUtils.consumerProps(embeddedKafkaBroker, "dlt-verify-consumer", false);
      DefaultKafkaConsumerFactory<String, String> consumerFactory =
          new DefaultKafkaConsumerFactory<>(consumerProps);
      Consumer<String, String> dltConsumer = consumerFactory.createConsumer();
      embeddedKafkaBroker.consumeFromAnEmbeddedTopic(dltConsumer, DLT_TOPIC);

      // when
      kafkaTemplate.send(NotificationKafkaTopics.NOTIFICATION_REQUESTED, payload);

      // then
      ConsumerRecord<String, String> dltRecord =
          KafkaTestUtils.getSingleRecord(dltConsumer, DLT_TOPIC, Duration.ofSeconds(10));
      assertThat(dltRecord.value()).isEqualTo(payload);
      verify(notificationService, times(3)).create(any(), any());

      dltConsumer.close();
    }
  }
}