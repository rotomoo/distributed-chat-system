package com.distributed.chat.system.kafka.producer.web.service;

import static org.mockito.Mockito.verify;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.kafka.core.KafkaTemplate;
import protobuf.EdaChatMessage.ChatMessage;

@DisplayName("[단위] EventProducerService 테스트")
@ExtendWith(MockitoExtension.class)
class EventProducerServiceUnitTest {

    @Mock
    private KafkaTemplate<String, byte[]> kafkaTemplate;

    @InjectMocks
    private EventProducerService eventProducerService;

    @Test
    @DisplayName("success case : 채널 ID 를 키로 발행해서 같은 채널 메시지는 같은 파티션으로 간다")
    void 채널_ID_를_키로_발행한다() {
        // arrange
        ChatMessage chatMessage = ChatMessage.newBuilder()
            .setSendUserId(5L).setChannelId(7L).setMessage("hello").setPublishDt(1_700_000_000_000L).build();

        // act
        eventProducerService.publishChatSendMessage(chatMessage);

        // assert
        verify(kafkaTemplate).send("chat-send-message", "7", chatMessage.toByteArray());
    }
}
