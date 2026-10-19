package com.distributed.chat.system.message.consumer.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import com.distributed.chat.system.common.event.ChatMessageEvent;
import com.distributed.chat.system.common.event.ChatNotificationEvent;
import com.distributed.chat.system.message.consumer.feign.KafkaProducerClient;
import feign.FeignException;
import feign.Request;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.concurrent.RejectedExecutionException;
import java.util.stream.LongStream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@DisplayName("[단위] ChatNotificationPublisher 테스트")
@ExtendWith(MockitoExtension.class)
class ChatNotificationPublisherUnitTest {

    private static final String MESSAGE_ID = "6acc66675f99715e286f0f70";
    private static final long SENDER = 5L;
    private static final long CHANNEL_ID = 7L;

    @Mock
    private KafkaProducerClient kafkaProducerClient;

    private ChatNotificationPublisher chatNotificationPublisher;

    private final ChatMessageEvent chatMessage = new ChatMessageEvent("event-1", SENDER, CHANNEL_ID, "점심 뭐 먹을까요?", 1_700_000_000_000L,
        List.of());

    @BeforeEach
    void setup() {
        // arrange : 알림 스레드 대신 호출한 스레드에서 바로 실행 (보낸 결과를 바로 확인)
        chatNotificationPublisher = new ChatNotificationPublisher(kafkaProducerClient, Runnable::run);
    }

    @Test
    @DisplayName("success case : 오프라인 멤버에게 알림 이벤트 한 건을 kafka-producer 에 발행 요청한다")
    void 오프라인_멤버에게_알림_이벤트_한_건을_kafka_producer_에_발행_요청한다() {
        // act
        int published = chatNotificationPublisher.publish(MESSAGE_ID, chatMessage, 2, List.of(9L, 17L));

        // assert
        assertThat(published).isEqualTo(1);
        List<ChatNotificationEvent> sent = sentNotifications(1);
        ChatNotificationEvent notification = sent.get(0);
        assertThat(notification.messageId()).isEqualTo(MESSAGE_ID);
        assertThat(notification.channelId()).isEqualTo(CHANNEL_ID);
        assertThat(notification.senderId()).isEqualTo(SENDER);
        assertThat(notification.preview()).isEqualTo("점심 뭐 먹을까요?");
        assertThat(notification.attachmentCount()).isEqualTo(2);
        assertThat(notification.publishDt()).isEqualTo(1_700_000_000_000L);
        assertThat(notification.receiverUserIds()).containsExactly(9L, 17L);
    }

    @Test
    @DisplayName("success case : 보낸 사람은 오프라인이어도 받는 사람에서 뺀다")
    void 보낸_사람은_오프라인이어도_받는_사람에서_뺀다() {
        // act
        chatNotificationPublisher.publish(MESSAGE_ID, chatMessage, 0, List.of(9L, SENDER, 17L));

        // assert
        assertThat(sentNotifications(1).get(0).receiverUserIds()).containsExactly(9L, 17L);
    }

    @Test
    @DisplayName("success case : 오프라인이 아무도 없거나 보낸 사람뿐이면 발행하지 않는다")
    void 오프라인이_아무도_없거나_보낸_사람뿐이면_발행하지_않는다() {
        // act
        int none = chatNotificationPublisher.publish(MESSAGE_ID, chatMessage, 0, List.of());
        int senderOnly = chatNotificationPublisher.publish(MESSAGE_ID, chatMessage, 0, List.of(SENDER));

        // assert
        assertThat(none).isZero();
        assertThat(senderOnly).isZero();
        verify(kafkaProducerClient, never()).publishChatNotification(any());
    }

    @Test
    @DisplayName("success case : 딱 500 명이면 한 건으로 보낸다")
    void 딱_500_명이면_한_건으로_보낸다() {
        // act
        int published = chatNotificationPublisher.publish(MESSAGE_ID, chatMessage, 0, userIds(500));

        // assert
        assertThat(published).isEqualTo(1);
        assertThat(sentNotifications(1).get(0).receiverUserIds()).hasSize(500);
    }

    @Test
    @DisplayName("success case : 1200 명이면 500 / 500 / 200 명씩 세 건으로 나눠 같은 채널로 보낸다")
    void 천이백_명이면_500_500_200_명씩_세_건으로_나눠_같은_채널로_보낸다() {
        // arrange
        List<Long> offline = userIds(1200);

        // act
        int published = chatNotificationPublisher.publish(MESSAGE_ID, chatMessage, 0, offline);

        // assert
        assertThat(published).isEqualTo(3);
        List<ChatNotificationEvent> sent = sentNotifications(3);
        assertThat(sent).extracting(notification -> notification.receiverUserIds().size()).containsExactly(500, 500, 200);
        assertThat(sent.stream().flatMap(notification -> notification.receiverUserIds().stream()).toList())
            .containsExactlyElementsOf(offline);
        assertThat(sent).extracting(ChatNotificationEvent::messageId).containsOnly(MESSAGE_ID);
        assertThat(sent).extracting(ChatNotificationEvent::channelId).containsOnly(CHANNEL_ID);
    }

    @Test
    @DisplayName("success case : 미리보기는 본문 앞 100자까지만 담는다")
    void 미리보기는_본문_앞_100자까지만_담는다() {
        // arrange
        ChatMessageEvent longMessage = new ChatMessageEvent("event-1", SENDER, CHANNEL_ID, "가".repeat(300), 1L, List.of());

        // act
        chatNotificationPublisher.publish(MESSAGE_ID, longMessage, 0, List.of(9L));

        // assert
        assertThat(sentNotifications(1).get(0).preview()).isEqualTo("가".repeat(100));
    }

    @Test
    @DisplayName("fail case : kafka-producer 호출이 실패해도 밖으로 던지지 않고 다음 묶음을 계속 보낸다 (저장 / 전달을 다시 하지 않는다, 로그만)")
    void kafka_producer_호출이_실패해도_밖으로_던지지_않고_다음_묶음을_계속_보낸다() {
        // arrange
        Request request = Request.create(Request.HttpMethod.POST, "/v1/private/event/publish/chat-notification", Map.of(), null,
            StandardCharsets.UTF_8, null);
        doThrow(new FeignException.ServiceUnavailable("kafka-producer down", request, null, Map.of()))
            .doNothing()
            .when(kafkaProducerClient).publishChatNotification(any());

        // act & assert
        assertThatCode(() -> chatNotificationPublisher.publish(MESSAGE_ID, chatMessage, 0, userIds(501))).doesNotThrowAnyException();
        verify(kafkaProducerClient, times(2)).publishChatNotification(any());
    }

    @Test
    @DisplayName("fail case : 알림 대기열이 가득 차서 넣지 못해도 밖으로 던지지 않는다 (그 알림은 버린다)")
    void 알림_대기열이_가득_차서_넣지_못해도_밖으로_던지지_않는다() {
        // arrange
        ChatNotificationPublisher full = new ChatNotificationPublisher(kafkaProducerClient, runnable -> {
            throw new RejectedExecutionException("queue full");
        });

        // act & assert
        assertThatCode(() -> full.publish(MESSAGE_ID, chatMessage, 0, List.of(9L))).doesNotThrowAnyException();
        verify(kafkaProducerClient, never()).publishChatNotification(any());
    }

    /**
     * kafka-producer 에 발행 요청한 알림 이벤트
     */
    private List<ChatNotificationEvent> sentNotifications(int count) {
        ArgumentCaptor<ChatNotificationEvent> notifications = ArgumentCaptor.forClass(ChatNotificationEvent.class);
        verify(kafkaProducerClient, times(count)).publishChatNotification(notifications.capture());
        return notifications.getAllValues();
    }

    private static List<Long> userIds(int count) {
        return LongStream.rangeClosed(100, 99 + count).boxed().toList();
    }
}
