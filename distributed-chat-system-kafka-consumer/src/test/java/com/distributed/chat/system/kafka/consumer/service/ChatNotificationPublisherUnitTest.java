package com.distributed.chat.system.kafka.consumer.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.distributed.chat.system.common.event.ChatMessageEvent;
import com.distributed.chat.system.common.event.ChatNotificationEvent;
import com.distributed.chat.system.common.event.EventJson;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.stream.LongStream;
import org.apache.kafka.common.errors.TimeoutException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;

@DisplayName("[단위] ChatNotificationPublisher 테스트")
@ExtendWith(MockitoExtension.class)
class ChatNotificationPublisherUnitTest {

    private static final String MESSAGE_ID = "6acc66675f99715e286f0f70";
    private static final long SENDER = 5L;
    private static final long CHANNEL_ID = 7L;

    @Mock
    private KafkaTemplate<String, String> kafkaTemplate;

    private ChatNotificationPublisher chatNotificationPublisher;

    private final ChatMessageEvent chatMessage = new ChatMessageEvent("event-1", SENDER, CHANNEL_ID, "점심 뭐 먹을까요?", 1_700_000_000_000L,
        List.of());

    @BeforeEach
    void setup() {
        chatNotificationPublisher = new ChatNotificationPublisher(kafkaTemplate);
    }

    @Test
    @DisplayName("success case : 오프라인 멤버에게 알림 이벤트 한 건을 chat-notification 에 channelId 키로 발행한다")
    void 오프라인_멤버에게_알림_이벤트_한_건을_chat_notification_에_channelId_키로_발행한다() {
        // arrange
        givenSendSucceeds();

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
        verify(kafkaTemplate).send(eq("chat-notification"), eq("7"), anyString());
    }

    @Test
    @DisplayName("success case : 보낸 사람은 오프라인이어도 받는 사람에서 뺀다")
    void 보낸_사람은_오프라인이어도_받는_사람에서_뺀다() {
        // arrange
        givenSendSucceeds();

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
        verify(kafkaTemplate, never()).send(anyString(), anyString(), anyString());
    }

    @Test
    @DisplayName("success case : 딱 500 명이면 한 건으로 보낸다")
    void 딱_500_명이면_한_건으로_보낸다() {
        // arrange
        givenSendSucceeds();

        // act
        int published = chatNotificationPublisher.publish(MESSAGE_ID, chatMessage, 0, userIds(500));

        // assert
        assertThat(published).isEqualTo(1);
        assertThat(sentNotifications(1).get(0).receiverUserIds()).hasSize(500);
    }

    @Test
    @DisplayName("success case : 1200 명이면 500 / 500 / 200 명씩 세 건으로 나눠 같은 키로 보낸다")
    void 천이백_명이면_500_500_200_명씩_세_건으로_나눠_같은_키로_보낸다() {
        // arrange
        givenSendSucceeds();
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
        verify(kafkaTemplate, times(3)).send(eq("chat-notification"), eq("7"), anyString());
    }

    @Test
    @DisplayName("success case : 미리보기는 본문 앞 100자까지만 담는다")
    void 미리보기는_본문_앞_100자까지만_담는다() {
        // arrange
        givenSendSucceeds();
        ChatMessageEvent longMessage = new ChatMessageEvent("event-1", SENDER, CHANNEL_ID, "가".repeat(300), 1L, List.of());

        // act
        chatNotificationPublisher.publish(MESSAGE_ID, longMessage, 0, List.of(9L));

        // assert
        assertThat(sentNotifications(1).get(0).preview()).isEqualTo("가".repeat(100));
    }

    @Test
    @DisplayName("fail case : 발행이 비동기로 실패해도 예외를 던지지 않는다 (저장 / 전달을 다시 하지 않는다, 로그만)")
    void 발행이_비동기로_실패해도_예외를_던지지_않는다() {
        // arrange
        when(kafkaTemplate.send(anyString(), anyString(), anyString()))
            .thenReturn(CompletableFuture.failedFuture(new TimeoutException("broker down")));

        // act & assert
        assertThatCode(() -> chatNotificationPublisher.publish(MESSAGE_ID, chatMessage, 0, List.of(9L))).doesNotThrowAnyException();
    }

    @Test
    @DisplayName("fail case : send 자체가 예외를 던져도 밖으로 던지지 않고 다음 묶음을 계속 보낸다")
    void send_자체가_예외를_던져도_밖으로_던지지_않고_다음_묶음을_계속_보낸다() {
        // arrange
        when(kafkaTemplate.send(anyString(), anyString(), anyString()))
            .thenThrow(new TimeoutException("metadata timeout"))
            .thenReturn(CompletableFuture.completedFuture(null));

        // act & assert
        assertThatCode(() -> chatNotificationPublisher.publish(MESSAGE_ID, chatMessage, 0, userIds(501))).doesNotThrowAnyException();
        verify(kafkaTemplate, times(2)).send(eq("chat-notification"), eq("7"), anyString());
    }

    private void givenSendSucceeds() {
        when(kafkaTemplate.send(anyString(), anyString(), anyString()))
            .thenReturn(CompletableFuture.completedFuture(org.mockito.Mockito.mock(SendResult.class)));
    }

    /**
     * 발행한 값(JSON 문자열)을 다시 읽은 알림 이벤트
     */
    private List<ChatNotificationEvent> sentNotifications(int count) {
        ArgumentCaptor<String> values = ArgumentCaptor.forClass(String.class);
        verify(kafkaTemplate, times(count)).send(eq("chat-notification"), eq("7"), values.capture());
        return values.getAllValues().stream().map(value -> EventJson.read(value, ChatNotificationEvent.class)).toList();
    }

    private static List<Long> userIds(int count) {
        return LongStream.rangeClosed(100, 99 + count).boxed().toList();
    }
}
