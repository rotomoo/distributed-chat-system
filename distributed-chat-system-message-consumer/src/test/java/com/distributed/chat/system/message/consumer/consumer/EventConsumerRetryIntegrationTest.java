package com.distributed.chat.system.message.consumer.consumer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.atLeast;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import com.distributed.chat.system.common.event.ChatMessageEvent;
import com.distributed.chat.system.common.event.ChatReactionEvent;
import com.distributed.chat.system.common.event.EventJson;
import com.distributed.chat.system.common.event.MalformedEventException;
import com.distributed.chat.system.message.consumer.base.kafka.DeadLetterHandler;
import com.distributed.chat.system.message.consumer.service.ChatMessageDeliveryService;
import com.distributed.chat.system.message.consumer.service.ChatNotificationPublisher;
import com.distributed.chat.system.message.consumer.service.ChatReceivers;
import com.distributed.chat.system.message.consumer.service.DeliveryFailedException;
import com.distributed.chat.system.mongodb.repository.ChatMessageRepository;
import com.distributed.chat.system.mysql.repository.ChannelUserRepository;
import com.distributed.chat.system.mongodb.repository.ChatMessageSaveRepositoryImpl;
import io.micrometer.core.instrument.MeterRegistry;
import java.math.BigInteger;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.common.serialization.StringSerializer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.boot.test.mock.mockito.SpyBean;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.kafka.core.DefaultKafkaProducerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.test.EmbeddedKafkaBroker;
import org.springframework.kafka.test.context.EmbeddedKafka;
import org.springframework.kafka.retrytopic.RetryTopicHeaders;
import org.springframework.kafka.support.KafkaHeaders;
import org.springframework.kafka.test.utils.KafkaTestUtils;

/**
 * Kafka 는 테스트 안에 띄운 진짜 브로커, MongoDB 는 로컬 스택(다른 모듈 통합 테스트와 같음), 채팅 서버 전달만 목.
 * 재시도 대기는 100 / 200 / 300ms 로 줄인다
 */
@DisplayName("[통합] 메시지 컨슈머 재시도 토픽 / DLT / eventId 한 번만 저장 테스트")
@SpringBootTest(properties = {
    "spring.kafka.bootstrap-servers=${spring.embedded.kafka.brokers}",
    "spring.kafka.consumer.auto-offset-reset=earliest",
    "chat.kafka.retry.delays-ms=100,200,300",
    "chat.kafka.retry.topic-partitions=1",
    "eureka.client.enabled=false"
})
@EmbeddedKafka(partitions = 1, topics = {"chat-send-message", "chat-reaction"})
class EventConsumerRetryIntegrationTest {

    private static final Duration WAIT = Duration.ofSeconds(30);

    @Autowired
    private EmbeddedKafkaBroker embeddedKafkaBroker;

    @Autowired
    private MeterRegistry meterRegistry;

    @Autowired
    @Qualifier("notificationMongoTemplate")
    private MongoTemplate mongoTemplate;

    @SpyBean
    private ChatMessageRepository chatMessageRepository;

    /**
     * 스파이한 리포지토리 프록시에서는 진짜 메서드를 부를 수 없어 저장 구현을 직접 부른다
     */
    @Autowired
    private ChatMessageSaveRepositoryImpl saveRepository;

    @MockBean
    private ChatMessageDeliveryService chatMessageDeliveryService;

    /**
     * 알림 발행 횟수만 본다 (처음 저장한 시도에서 한 번만. 실제 발행 경로는 EventConsumerEndToEndIntegrationTest)
     */
    @MockBean
    private ChatNotificationPublisher chatNotificationPublisher;

    @SpyBean
    private DeadLetterHandler deadLetterHandler;

    /**
     * 여기서는 재시도 / DLT 만 본다. 채널 멤버(MySQL)는 고정 (멤버 확인 / 실제 전달 경로는 EventConsumerEndToEndIntegrationTest)
     */
    @MockBean
    private ChannelUserRepository channelUserRepository;

    private KafkaTemplate<String, String> kafkaTemplate;
    private final List<String> eventIds = new ArrayList<>();

    @BeforeEach
    void setup() {
        org.mockito.Mockito.when(channelUserRepository.findMemberUserIds(org.mockito.ArgumentMatchers.anyLong())).thenReturn(List.of(5L, 1L));
        // 1 은 오프라인 (알림 대상)
        org.mockito.Mockito.when(chatMessageDeliveryService.locate(any())).thenReturn(new ChatReceivers(Map.of("10.0.0.1:1111", List.of(5L)), List.of(1L)));
        Map<String, Object> props = KafkaTestUtils.producerProps(embeddedKafkaBroker);
        props.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class);
        props.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, StringSerializer.class);
        kafkaTemplate = new KafkaTemplate<>(new DefaultKafkaProducerFactory<>(props));
    }

    @AfterEach
    void tearDown() {
        mongoTemplate.remove(Query.query(Criteria.where("eventId").in(eventIds)), "chatMessage");
        kafkaTemplate.destroy();
    }

    @Test
    @DisplayName("success case : 저장이 두 번 실패한 뒤 성공하면 문서는 하나이고 그 문서로 한 번 전달한다")
    void 저장이_두_번_실패한_뒤_성공하면_문서는_하나이고_그_문서로_한_번_전달한다() {
        // given
        String eventId = newEventId();
        doThrow(new IllegalStateException("mongo down 1")).doThrow(new IllegalStateException("mongo down 2"))
            .doAnswer(invocation -> saveRepository.saveOnce(invocation.getArgument(0)))
            .when(chatMessageRepository).saveOnce(argThat(message -> message != null && eventId.equals(message.getEventId())));

        // when
        publish(eventId, "두 번 실패 후 성공");

        // then
        await().atMost(WAIT).untilAsserted(() -> verify(chatMessageDeliveryService).deliver(anyString(), withEventId(eventId), any(), any()));
        assertThat(documents(eventId)).isEqualTo(1);
        verify(chatMessageRepository, times(3)).saveOnce(argThat(message -> message != null && eventId.equals(message.getEventId())));
        verify(chatMessageDeliveryService, times(1)).deliver(anyString(), withEventId(eventId), any(), any());
        // 세 번째 시도에서 처음 저장됐으므로 알림은 그때 한 번
        verify(chatNotificationPublisher, times(1)).publish(anyString(), withEventId(eventId), eq(0), eq(List.of(1L)));
    }

    @Test
    @DisplayName("fail case : 저장이 계속 실패하면 재시도를 다 쓰고 DLT 로 가서 dead letter 로 분류된다")
    void 저장이_계속_실패하면_재시도를_다_쓰고_DLT_로_가서_dead_letter_로_분류된다() {
        // given
        String eventId = newEventId();
        doThrow(new IllegalStateException("mongo down"))
            .when(chatMessageRepository).saveOnce(argThat(message -> message != null && eventId.equals(message.getEventId())));

        // when
        publish(eventId, "항상 실패");

        // then
        assertThat(dltOutcome(eventId)).isEqualTo(DeadLetterHandler.Outcome.DEAD_LETTER);
        assertThat(attempts(dltRecord(eventId))).isEqualTo(5);
        assertThat(documents(eventId)).isZero();
        verify(chatMessageRepository, times(4)).saveOnce(argThat(message -> message != null && eventId.equals(message.getEventId())));
        verify(chatMessageDeliveryService, times(0)).deliver(anyString(), withEventId(eventId), any(), any());
        verify(chatNotificationPublisher, times(0)).publish(anyString(), withEventId(eventId), eq(0), any());
    }

    @Test
    @DisplayName("success case : 같은 eventId 가 두 번 발행돼도 문서는 하나이고 두 번 다 같은 messageId 로 전달한다")
    void 같은_eventId_가_두_번_발행돼도_문서는_하나이고_두_번_다_같은_messageId_로_전달한다() {
        // given
        String eventId = newEventId();

        // when
        publish(eventId, "중복 발행");
        publish(eventId, "중복 발행");

        // then
        ArgumentCaptor<String> messageIds = ArgumentCaptor.forClass(String.class);
        await().atMost(WAIT).untilAsserted(() ->
            verify(chatMessageDeliveryService, times(2)).deliver(messageIds.capture(), withEventId(eventId), any(), any()));
        assertThat(messageIds.getAllValues()).hasSize(2).containsOnly(messageIds.getAllValues().get(0));
        assertThat(documents(eventId)).isEqualTo(1);
        // 두 번째 발행은 이미 저장된 문서를 받으므로 알림을 다시 만들지 않는다
        verify(chatNotificationPublisher, times(1)).publish(eq(messageIds.getValue()), withEventId(eventId), eq(0), eq(List.of(1L)));
    }

    @Test
    @DisplayName("success case : 전달이 실패하면 재시도 토픽으로 다시 전달하고 문서는 늘지 않는다 (같은 messageId)")
    void 전달이_실패하면_재시도_토픽으로_다시_전달하고_문서는_늘지_않는다() {
        // given
        String eventId = newEventId();
        doThrow(new DeliveryFailedException(List.of("10.0.0.1:1111"))).doNothing()
            .when(chatMessageDeliveryService).deliver(anyString(), withEventId(eventId), any(), any());

        // when
        publish(eventId, "전달 실패 후 성공");

        // then
        ArgumentCaptor<String> messageIds = ArgumentCaptor.forClass(String.class);
        await().atMost(WAIT).untilAsserted(() ->
            verify(chatMessageDeliveryService, times(2)).deliver(messageIds.capture(), withEventId(eventId), any(), any()));
        assertThat(messageIds.getAllValues()).containsOnly(messageIds.getAllValues().get(0));
        assertThat(documents(eventId)).isEqualTo(1);
        verify(chatNotificationPublisher, times(1)).publish(eq(messageIds.getValue()), withEventId(eventId), eq(0), eq(List.of(1L)));
        verify(chatMessageRepository, atLeast(2)).saveOnce(argThat(message -> message != null && eventId.equals(message.getEventId())));
    }

    @Test
    @DisplayName("fail case : 전달이 계속 실패하면 DLT 에서 저장된 것을 확인하고 전달 버림으로 분류된다")
    void 전달이_계속_실패하면_DLT_에서_저장된_것을_확인하고_전달_버림으로_분류된다() {
        // given
        String eventId = newEventId();
        doThrow(new DeliveryFailedException(List.of("10.0.0.1:1111")))
            .when(chatMessageDeliveryService).deliver(anyString(), withEventId(eventId), any(), any());

        // when
        publish(eventId, "전달 계속 실패");

        // then
        assertThat(dltOutcome(eventId)).isEqualTo(DeadLetterHandler.Outcome.DELIVERY_DROPPED);
        verify(chatMessageDeliveryService, times(4)).deliver(anyString(), withEventId(eventId), any(), any());
        assertThat(documents(eventId)).isEqualTo(1);
        // 전달은 네 번 시도했지만 알림은 처음 저장한 첫 시도에서 한 번만
        verify(chatNotificationPublisher, times(1)).publish(anyString(), withEventId(eventId), eq(0), eq(List.of(1L)));
    }

    @Test
    @DisplayName("fail case : 리액션 전달이 계속 실패하면 재시도 뒤 DLT 에서 전달 버림으로 분류된다 (갱신은 그대로 한 번)")
    void 리액션_전달이_계속_실패하면_재시도_뒤_DLT_에서_전달_버림으로_분류된다() {
        // given
        com.distributed.chat.system.mongodb.collection.ChatMessage target = chatMessageRepository.saveOnce(
            com.distributed.chat.system.mongodb.collection.ChatMessage.builder().eventId(newEventId()).channelId(7L)
                .content("리액션 대상").createUserId(1L).build());
        String messageId = target.getId().toHexString();
        doThrow(new DeliveryFailedException(List.of("10.0.0.1:1111")))
            .when(chatMessageDeliveryService).deliverReaction(eq(7L), eq(messageId), any(), any());

        // when
        kafkaTemplate.send("chat-reaction", "7", EventJson.write(new ChatReactionEvent(5L, 7L, messageId, "👍",
            ChatReactionEvent.Action.ADD, 0L))).join();

        // then
        await().atMost(WAIT).until(() -> dltRecords().getAllValues().stream().anyMatch(candidate -> isReactionTo(candidate, messageId)));
        ConsumerRecord<String, String> record = dltRecords().getAllValues().stream()
            .filter(candidate -> isReactionTo(candidate, messageId)).findFirst().orElseThrow();
        assertThat(deadLetterHandler.classify(record)).isEqualTo(DeadLetterHandler.Outcome.DELIVERY_DROPPED);
        verify(chatMessageDeliveryService, times(4)).deliverReaction(eq(7L), eq(messageId), any(), any());
        assertThat(chatMessageRepository.findById(target.getId()).orElseThrow().getReactions())
            .containsExactly(new com.distributed.chat.system.mongodb.collection.ChatMessage.Reaction("👍", List.of(5L)));
    }

    @Test
    @DisplayName("fail case : 이벤트로 읽을 수 없는 메시지 / 리액션(깨진 JSON, 예전 바이너리 값)은 재시도 없이 바로 DLT 로 가서 malformed 로 분류되고 다음 레코드는 그대로 처리된다")
    void 이벤트로_읽을_수_없는_값은_재시도_없이_바로_DLT_로_가서_malformed_로_분류된다() {
        // given
        String brokenKey = "broken-" + UUID.randomUUID();
        String binaryKey = "binary-" + UUID.randomUUID();
        String reactionKey = "reaction-" + UUID.randomUUID();
        String oldBinary = new String(new byte[]{0x08, 0x05, 0x10, 0x07, 0x1a, 0x02, 'h', 'i'}, StandardCharsets.UTF_8);
        String eventId = newEventId();

        // when : 읽을 수 없는 값 뒤에 정상 메시지
        kafkaTemplate.send("chat-send-message", brokenKey, "{\"channelId\":").join();
        kafkaTemplate.send("chat-send-message", binaryKey, oldBinary).join();
        kafkaTemplate.send("chat-reaction", reactionKey, "not json").join();
        publish(eventId, "뒤의 정상 메시지");

        // then
        await().atMost(WAIT).untilAsserted(() -> verify(chatMessageDeliveryService).deliver(anyString(), withEventId(eventId), any(), any()));
        for (String key : List.of(brokenKey, binaryKey, reactionKey)) {
            ConsumerRecord<String, String> record = dltRecordByKey(key);
            assertThat(record.topic()).endsWith("-dlt");
            assertThat(deadLetterHandler.classify(record)).isEqualTo(DeadLetterHandler.Outcome.MALFORMED);
            // 원래 토픽에서 1번 실패하고 바로 DLT (재시도 토픽을 거쳤으면 5)
            assertThat(attempts(record)).as(key).isEqualTo(2);
            assertThat(header(record, KafkaHeaders.EXCEPTION_FQCN) + header(record, KafkaHeaders.EXCEPTION_CAUSE_FQCN))
                .contains(MalformedEventException.class.getName());
        }
        assertThat(documents(eventId)).isEqualTo(1);
    }

    @Test
    @DisplayName("success case : Kafka 컨슈머 클라이언트 기본 메트릭(lag 등)이 메트릭 레지스트리에 붙어 있다 (/actuator/prometheus)")
    void Kafka_컨슈머_클라이언트_메트릭이_메트릭_레지스트리에_붙어_있다() {
        // when & then
        await().atMost(WAIT).untilAsserted(() ->
            assertThat(meterRegistry.find("kafka.consumer.fetch.manager.records.lag.max").meters()).isNotEmpty());
    }

    /**
     * DLT 핸들러가 받은 그 eventId 레코드 (DLT 토픽에서 왔는지 확인) 의 분류
     */
    private DeadLetterHandler.Outcome dltOutcome(String eventId) {
        ConsumerRecord<String, String> record = dltRecord(eventId);
        assertThat(record.topic()).isEqualTo("chat-send-message-dlt");
        return deadLetterHandler.classify(record);
    }

    /**
     * DLT 핸들러가 받은 그 eventId 레코드
     */
    private ConsumerRecord<String, String> dltRecord(String eventId) {
        return dltRecords().getAllValues().stream()
            .filter(candidate -> eventId.equals(parse(candidate).eventId()))
            .findFirst().orElseThrow();
    }

    /**
     * DLT 핸들러가 받은 그 key 의 레코드 (나타날 때까지 기다린다)
     */
    private ConsumerRecord<String, String> dltRecordByKey(String key) {
        await().atMost(WAIT).until(() -> dltRecords().getAllValues().stream().anyMatch(candidate -> key.equals(candidate.key())));
        return dltRecords().getAllValues().stream().filter(candidate -> key.equals(candidate.key())).findFirst().orElseThrow();
    }

    @SuppressWarnings("unchecked")
    private ArgumentCaptor<ConsumerRecord<String, String>> dltRecords() {
        ArgumentCaptor<ConsumerRecord<String, String>> captor = ArgumentCaptor.forClass(ConsumerRecord.class);
        await().atMost(WAIT).untilAsserted(() -> verify(deadLetterHandler, atLeast(1)).handle(captor.capture()));
        return captor;
    }

    /**
     * 재시도 토픽이 레코드에 붙이는 시도 수 (원래 토픽 1번 실패 → DLT 면 2)
     */
    private static int attempts(ConsumerRecord<String, String> record) {
        byte[] value = record.headers().lastHeader(RetryTopicHeaders.DEFAULT_HEADER_ATTEMPTS).value();
        return value.length == Integer.BYTES ? ByteBuffer.wrap(value).getInt() : new BigInteger(value).intValue();
    }

    private static String header(ConsumerRecord<String, String> record, String name) {
        org.apache.kafka.common.header.Header header = record.headers().lastHeader(name);
        return header == null ? "" : new String(header.value(), StandardCharsets.UTF_8);
    }

    /**
     * 이 메시지에 대한 리액션 DLT 레코드인지 (다른 테스트가 보낸 리액션 / 읽을 수 없는 값과 섞이지 않게)
     */
    private static boolean isReactionTo(ConsumerRecord<String, String> record, String messageId) {
        return record.topic().equals("chat-reaction-dlt") && record.value() != null && record.value().contains(messageId);
    }

    private static ChatMessageEvent parse(ConsumerRecord<String, String> record) {
        try {
            return EventJson.read(record.value(), ChatMessageEvent.class);
        } catch (MalformedEventException e) {
            return ChatMessageEvent.empty();
        }
    }

    private ChatMessageEvent withEventId(String eventId) {
        return argThat(message -> message != null && eventId.equals(message.eventId()));
    }

    private void publish(String eventId, String text) {
        ChatMessageEvent chatMessage = new ChatMessageEvent(eventId, 5L, 7L, text, System.currentTimeMillis(), List.of());
        kafkaTemplate.send("chat-send-message", "7", EventJson.write(chatMessage)).join();
    }

    private long documents(String eventId) {
        return mongoTemplate.count(Query.query(Criteria.where("eventId").is(eventId)), "chatMessage");
    }

    private String newEventId() {
        String eventId = UUID.randomUUID().toString();
        eventIds.add(eventId);
        return eventId;
    }
}
