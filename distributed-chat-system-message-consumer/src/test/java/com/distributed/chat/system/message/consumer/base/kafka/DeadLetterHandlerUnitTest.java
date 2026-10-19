package com.distributed.chat.system.message.consumer.base.kafka;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.distributed.chat.system.common.event.ChatMessageEvent;
import com.distributed.chat.system.common.event.ChatReactionEvent;
import com.distributed.chat.system.common.event.EventJson;
import com.distributed.chat.system.common.event.MalformedEventException;
import com.distributed.chat.system.message.consumer.service.DeliveryFailedException;
import com.distributed.chat.system.mongodb.collection.ChatMessage;
import com.distributed.chat.system.mongodb.repository.ChatMessageRepository;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Optional;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.kafka.support.KafkaHeaders;

@DisplayName("[단위] DeadLetterHandler 테스트")
@ExtendWith(MockitoExtension.class)
class DeadLetterHandlerUnitTest {

    @Mock
    private ChatMessageRepository chatMessageRepository;

    @InjectMocks
    private DeadLetterHandler deadLetterHandler;

    private final String chatMessage = EventJson.write(new ChatMessageEvent("event-1", 5L, 7L, "hello", 1L, List.of()));

    private final String reaction = EventJson.write(new ChatReactionEvent(5L, 7L, "6acc66675f99715e286f0f6e", "👍",
        ChatReactionEvent.Action.ADD, 1L));

    @Test
    @DisplayName("success case : 저장된 메시지(eventId 있음)가 DLT 에 오면 전달만 버린 것이다")
    void 저장된_메시지가_DLT_에_오면_전달만_버린_것이다() {
        // arrange
        when(chatMessageRepository.findByEventId("event-1")).thenReturn(Optional.of(ChatMessage.builder().eventId("event-1").build()));

        // act
        DeadLetterHandler.Outcome outcome = deadLetterHandler.classify(
            dltRecord("chat-send-message", chatMessage, DeliveryFailedException.class.getName()));

        // assert
        assertThat(outcome).isEqualTo(DeadLetterHandler.Outcome.DELIVERY_DROPPED);
    }

    @Test
    @DisplayName("fail case : 저장 못 한 메시지가 DLT 에 오면 dead letter 다 (다시 발행 대상)")
    void 저장_못_한_메시지가_DLT_에_오면_dead_letter_다() {
        // arrange
        when(chatMessageRepository.findByEventId("event-1")).thenReturn(Optional.empty());

        // act
        DeadLetterHandler.Outcome outcome = deadLetterHandler.classify(
            dltRecord("chat-send-message", chatMessage, IllegalStateException.class.getName()));

        // assert
        assertThat(outcome).isEqualTo(DeadLetterHandler.Outcome.DEAD_LETTER);
    }

    @Test
    @DisplayName("success case : 로그만 남기고 예외 없이 끝난다 (DLT 레코드는 그대로 남는다)")
    void 로그만_남기고_예외_없이_끝난다() {
        // arrange
        when(chatMessageRepository.findByEventId("event-1")).thenReturn(Optional.empty());

        // act & assert
        assertThatCode(() -> deadLetterHandler.handle(
                dltRecord("chat-send-message", chatMessage, IllegalStateException.class.getName())))
            .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("success case : 리액션은 마지막 예외가 전달 실패면 전달만 버린 것, 아니면 dead letter 다")
    void 리액션은_마지막_예외가_전달_실패면_전달만_버린_것_아니면_dead_letter_다() {
        // act
        DeadLetterHandler.Outcome deliveryFailed = deadLetterHandler.classify(
            dltRecord("chat-reaction", reaction, DeliveryFailedException.class.getName()));
        DeadLetterHandler.Outcome applyFailed = deadLetterHandler.classify(
            dltRecord("chat-reaction", reaction, IllegalStateException.class.getName()));

        // assert
        assertThat(deliveryFailed).isEqualTo(DeadLetterHandler.Outcome.DELIVERY_DROPPED);
        assertThat(applyFailed).isEqualTo(DeadLetterHandler.Outcome.DEAD_LETTER);
    }

    @Test
    @DisplayName("fail case : 이벤트로 읽을 수 없는 메시지 / 리액션(예전 바이너리 값, 빈 값)은 malformed 로 분류하고 저장 여부를 보지 않는다")
    void 이벤트로_읽을_수_없는_메시지_리액션은_malformed_로_분류한다() {
        // arrange
        String binary = new String(new byte[]{0x08, 0x05, 0x10, 0x07}, StandardCharsets.UTF_8);
        String malformed = MalformedEventException.class.getName();

        // act
        DeadLetterHandler.Outcome message = deadLetterHandler.classify(dltRecord("chat-send-message", binary, malformed));
        DeadLetterHandler.Outcome emptyMessage = deadLetterHandler.classify(dltRecord("chat-send-message", null, malformed));
        DeadLetterHandler.Outcome reactionOutcome = deadLetterHandler.classify(dltRecord("chat-reaction", "{\"action\":", malformed));

        // assert
        assertThat(message).isEqualTo(DeadLetterHandler.Outcome.MALFORMED);
        assertThat(emptyMessage).isEqualTo(DeadLetterHandler.Outcome.MALFORMED);
        assertThat(reactionOutcome).isEqualTo(DeadLetterHandler.Outcome.MALFORMED);
        verifyNoInteractions(chatMessageRepository);
        assertThatCode(() -> deadLetterHandler.handle(dltRecord("chat-send-message", binary, malformed))).doesNotThrowAnyException();
        assertThatCode(() -> deadLetterHandler.handle(dltRecord("chat-reaction", "{\"action\":", malformed))).doesNotThrowAnyException();
    }

    @Test
    @DisplayName("success case : 재시도 토픽 이름(-retry-1000)이 원래 토픽 헤더여도 원래 토픽으로 판단한다")
    void 재시도_토픽_이름이_원래_토픽_헤더여도_원래_토픽으로_판단한다() {
        // act
        DeadLetterHandler.Outcome outcome = deadLetterHandler.classify(
            dltRecord("chat-reaction-retry-1000", reaction, DeliveryFailedException.class.getName()));

        // assert
        assertThat(outcome).isEqualTo(DeadLetterHandler.Outcome.DELIVERY_DROPPED);
    }

    private static ConsumerRecord<String, String> dltRecord(String originalTopic, String value, String causeFqcn) {
        ConsumerRecord<String, String> record = new ConsumerRecord<>(ChatTopics.mainTopicOf(originalTopic) + "-dlt", 0, 0L, "7", value);
        record.headers().add(KafkaHeaders.ORIGINAL_TOPIC, originalTopic.getBytes(StandardCharsets.UTF_8));
        record.headers().add(KafkaHeaders.EXCEPTION_CAUSE_FQCN, causeFqcn.getBytes(StandardCharsets.UTF_8));
        return record;
    }
}
