package com.distributed.chat.system.kafka.consumer.base.kafka;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import com.distributed.chat.system.kafka.consumer.service.DeliveryFailedException;
import com.distributed.chat.system.mongodb.collection.ChatMessage;
import com.distributed.chat.system.mongodb.repository.ChatMessageRepository;
import java.nio.charset.StandardCharsets;
import java.util.Optional;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.kafka.support.KafkaHeaders;
import protobuf.EdaChatMessage;
import protobuf.EdaChatMessage.ChatReaction;

@DisplayName("[단위] DeadLetterHandler 테스트")
@ExtendWith(MockitoExtension.class)
class DeadLetterHandlerUnitTest {

    @Mock
    private ChatMessageRepository chatMessageRepository;

    @InjectMocks
    private DeadLetterHandler deadLetterHandler;

    private final EdaChatMessage.ChatMessage chatMessage = EdaChatMessage.ChatMessage.newBuilder()
        .setEventId("event-1").setSendUserId(5L).setChannelId(7L).setMessage("hello").setPublishDt(1L).build();

    @Test
    @DisplayName("success case : 저장된 메시지(eventId 있음)가 DLT 에 오면 전달만 버린 것이다")
    void 저장된_메시지가_DLT_에_오면_전달만_버린_것이다() {
        // arrange
        when(chatMessageRepository.findByEventId("event-1")).thenReturn(Optional.of(ChatMessage.builder().eventId("event-1").build()));

        // act
        DeadLetterHandler.Outcome outcome = deadLetterHandler.classify(
            dltRecord("chat-send-message", chatMessage.toByteArray(), DeliveryFailedException.class.getName()));

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
            dltRecord("chat-send-message", chatMessage.toByteArray(), IllegalStateException.class.getName()));

        // assert
        assertThat(outcome).isEqualTo(DeadLetterHandler.Outcome.DEAD_LETTER);
    }

    @Test
    @DisplayName("success case : 로그만 남기고 예외 없이 끝난다 (DLT 레코드는 그대로 남는다)")
    void 로그만_남기고_예외_없이_끝난다() {
        // arrange
        when(chatMessageRepository.findByEventId("event-1")).thenReturn(Optional.empty());

        // act & assert
        org.assertj.core.api.Assertions.assertThatCode(() -> deadLetterHandler.handle(
                dltRecord("chat-send-message", chatMessage.toByteArray(), IllegalStateException.class.getName())))
            .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("success case : 리액션은 마지막 예외가 전달 실패면 전달만 버린 것, 아니면 dead letter 다")
    void 리액션은_마지막_예외가_전달_실패면_전달만_버린_것_아니면_dead_letter_다() {
        // arrange
        byte[] reaction = ChatReaction.newBuilder().setMessageId("6acc66675f99715e286f0f6e").setChannelId(7L).setEmoji("👍")
            .setAction(ChatReaction.Action.ADD).build().toByteArray();

        // act
        DeadLetterHandler.Outcome deliveryFailed = deadLetterHandler.classify(
            dltRecord("chat-reaction", reaction, DeliveryFailedException.class.getName()));
        DeadLetterHandler.Outcome applyFailed = deadLetterHandler.classify(
            dltRecord("chat-reaction", reaction, IllegalStateException.class.getName()));

        // assert
        assertThat(deliveryFailed).isEqualTo(DeadLetterHandler.Outcome.DELIVERY_DROPPED);
        assertThat(applyFailed).isEqualTo(DeadLetterHandler.Outcome.DEAD_LETTER);
    }

    private static ConsumerRecord<String, byte[]> dltRecord(String originalTopic, byte[] value, String causeFqcn) {
        ConsumerRecord<String, byte[]> record = new ConsumerRecord<>(originalTopic + "-dlt", 0, 0L, "7", value);
        record.headers().add(KafkaHeaders.ORIGINAL_TOPIC, originalTopic.getBytes(StandardCharsets.UTF_8));
        record.headers().add(KafkaHeaders.EXCEPTION_CAUSE_FQCN, causeFqcn.getBytes(StandardCharsets.UTF_8));
        return record;
    }
}
