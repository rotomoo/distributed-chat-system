package com.distributed.chat.system.kafka.consumer.consumer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.distributed.chat.system.kafka.consumer.service.ChatMessageDeliveryService;
import com.distributed.chat.system.mongodb.repository.ChatMessageRepository;
import java.time.Instant;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import protobuf.EdaChatMessage.ChatMessage;

@DisplayName("[단위] EventConsumer 테스트")
@ExtendWith(MockitoExtension.class)
class EventConsumerUnitTest {

    @Mock
    private ChatMessageRepository chatMessageRepository;

    @Mock
    private ChatMessageDeliveryService chatMessageDeliveryService;

    @InjectMocks
    private EventConsumer eventConsumer;

    @Test
    @DisplayName("success case : 메시지 이벤트를 이력에 저장한 뒤 저장된 ID 로 전달한다")
    void 메시지_이벤트를_이력에_저장한_뒤_저장된_ID_로_전달한다() {
        // arrange
        ChatMessage chatMessage = ChatMessage.newBuilder()
            .setSendUserId(5L).setChannelId(0L).setMessage("hello").setPublishDt(1_700_000_000_000L).build();
        when(chatMessageRepository.save(any())).thenAnswer(invocation ->
            com.distributed.chat.system.mongodb.collection.ChatMessage.builder().id("saved-id").build());

        // act
        eventConsumer.consumeChatSendMessage(chatMessage.toByteArray());

        // assert
        ArgumentCaptor<com.distributed.chat.system.mongodb.collection.ChatMessage> saved =
            ArgumentCaptor.forClass(com.distributed.chat.system.mongodb.collection.ChatMessage.class);
        verify(chatMessageRepository).save(saved.capture());
        assertThat(saved.getValue().getContent()).isEqualTo("hello");
        assertThat(saved.getValue().getCreateUserId()).isEqualTo(5L);
        assertThat(saved.getValue().getChannelId()).isEqualTo(0L);
        assertThat(saved.getValue().getEventPublishDt()).isEqualTo(Instant.ofEpochMilli(1_700_000_000_000L));
        verify(chatMessageDeliveryService).deliver(eq("saved-id"), eq(chatMessage));
    }

    @Test
    @DisplayName("fail case : 프로토콜 버퍼로 읽을 수 없는 메시지는 저장하지도 전달하지도 않는다")
    void 프로토콜_버퍼로_읽을_수_없는_메시지는_저장하지도_전달하지도_않는다() {
        // arrange
        byte[] broken = {(byte) 0xFF, (byte) 0xFF, (byte) 0xFF};

        // act
        eventConsumer.consumeChatSendMessage(broken);

        // assert
        verify(chatMessageRepository, never()).save(any());
        verifyNoInteractions(chatMessageDeliveryService);
    }
}
