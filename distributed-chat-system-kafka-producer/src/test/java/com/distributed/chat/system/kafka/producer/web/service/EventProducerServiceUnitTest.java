package com.distributed.chat.system.kafka.producer.web.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.util.concurrent.CompletableFuture;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;
import protobuf.EdaChatMessage.ChatMessage;
import protobuf.EdaChatMessage.ChatReaction;

@DisplayName("[단위] EventProducerService 테스트")
@ExtendWith(MockitoExtension.class)
class EventProducerServiceUnitTest {

    @Mock
    private KafkaTemplate<String, byte[]> kafkaTemplate;

    private final SimpleMeterRegistry meterRegistry = new SimpleMeterRegistry();

    private EventProducerService eventProducerService;

    @BeforeEach
    void setup() {
        eventProducerService = new EventProducerService(kafkaTemplate, meterRegistry);
    }

    @Test
    @DisplayName("success case : 채널 ID 를 키로 발행해서 같은 채널 메시지는 같은 파티션으로 간다")
    void 채널_ID_를_키로_발행한다() {
        // arrange
        ChatMessage chatMessage = ChatMessage.newBuilder()
            .setSendUserId(5L).setChannelId(7L).setMessage("hello").setPublishDt(1_700_000_000_000L).build();

        when(kafkaTemplate.send("chat-send-message", "7", chatMessage.toByteArray())).thenReturn(new CompletableFuture<>());

        // act
        eventProducerService.publishChatSendMessage(chatMessage);

        // assert
        verify(kafkaTemplate).send("chat-send-message", "7", chatMessage.toByteArray());
    }

    @Test
    @DisplayName("success case : 리액션은 chat-reaction 토픽에 채널 ID 를 키로 발행한다")
    void 리액션은_chat_reaction_토픽에_채널_ID_를_키로_발행한다() {
        // arrange
        ChatReaction chatReaction = ChatReaction.newBuilder()
            .setSendUserId(5L).setChannelId(7L).setMessageId("6acc66675f99715e286f0f6e").setEmoji("👍")
            .setAction(ChatReaction.Action.ADD).setPublishDt(1_700_000_000_000L).build();

        when(kafkaTemplate.send("chat-reaction", "7", chatReaction.toByteArray())).thenReturn(new CompletableFuture<>());

        // act
        eventProducerService.publishChatReaction(chatReaction);

        // assert
        verify(kafkaTemplate).send("chat-reaction", "7", chatReaction.toByteArray());
    }

    @Test
    @DisplayName("success case : 브로커가 받으면 성공으로, 실패하면 실패로 발행 시간 / 수를 센다")
    void 브로커가_받으면_성공으로_실패하면_실패로_발행_시간_수를_센다() {
        // arrange
        ChatMessage chatMessage = ChatMessage.newBuilder().setSendUserId(5L).setChannelId(7L).setMessage("hello").build();
        CompletableFuture<SendResult<String, byte[]>> acked = new CompletableFuture<>();
        CompletableFuture<SendResult<String, byte[]>> ackedAgain = new CompletableFuture<>();
        CompletableFuture<SendResult<String, byte[]>> failed = new CompletableFuture<>();
        when(kafkaTemplate.send("chat-send-message", "7", chatMessage.toByteArray())).thenReturn(acked, ackedAgain, failed);

        // act : 성공 2, 실패 1
        eventProducerService.publishChatSendMessage(chatMessage);
        eventProducerService.publishChatSendMessage(chatMessage);
        eventProducerService.publishChatSendMessage(chatMessage);
        acked.complete(null);
        ackedAgain.complete(null);
        failed.completeExceptionally(new IllegalStateException("broker down"));

        // assert
        assertThat(meterRegistry.get("chat.producer.publish").tag("topic", "chat-send-message").tag("result", "success").timer().count())
            .isEqualTo(2);
        assertThat(meterRegistry.get("chat.producer.publish").tag("topic", "chat-send-message").tag("result", "failure").timer().count())
            .isEqualTo(1);
    }
}
