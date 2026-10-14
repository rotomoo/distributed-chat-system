package com.distributed.chat.system.kafka.producer.web.service;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import com.distributed.chat.system.common.event.ChatMessageEvent;
import com.distributed.chat.system.common.event.ChatReactionEvent;
import com.distributed.chat.system.common.event.EventJson;
import com.distributed.chat.system.common.event.MalformedEventException;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.kafka.core.KafkaTemplate;

@DisplayName("[단위] EventProducerService 테스트")
@ExtendWith(MockitoExtension.class)
class EventProducerServiceUnitTest {

    @Mock
    private KafkaTemplate<String, String> kafkaTemplate;

    @InjectMocks
    private EventProducerService eventProducerService;

    @Test
    @DisplayName("success case : 채널 ID 를 키로 발행해서 같은 채널 메시지는 같은 파티션으로 간다 (값은 받은 JSON 그대로)")
    void 채널_ID_를_키로_발행한다() {
        // arrange
        String payload = EventJson.write(new ChatMessageEvent("event-1", 5L, 7L, "hello", 1_700_000_000_000L, List.of()));

        // act
        eventProducerService.publishChatSendMessage(payload);

        // assert
        verify(kafkaTemplate).send("chat-send-message", "7", payload);
    }

    @Test
    @DisplayName("success case : 리액션은 chat-reaction 토픽에 채널 ID 를 키로 발행한다")
    void 리액션은_chat_reaction_토픽에_채널_ID_를_키로_발행한다() {
        // arrange
        String payload = EventJson.write(new ChatReactionEvent(5L, 7L, "6acc66675f99715e286f0f6e", "👍", ChatReactionEvent.Action.ADD,
            1_700_000_000_000L));

        // act
        eventProducerService.publishChatReaction(payload);

        // assert
        verify(kafkaTemplate).send("chat-reaction", "7", payload);
    }

    @Test
    @DisplayName("success case : 이 서버가 모르는 새 필드도 빼지 않고 받은 문자열 그대로 발행한다 (컨슈머까지 간다)")
    void 모르는_새_필드도_빼지_않고_받은_문자열_그대로_발행한다() {
        // arrange
        String payload = "{\"eventId\":\"event-1\",\"sendUserId\":5,\"channelId\":7,\"message\":\"hi\",\"threadId\":\"t-1\"}";

        // act
        eventProducerService.publishChatSendMessage(payload);

        // assert
        verify(kafkaTemplate).send("chat-send-message", "7", payload);
    }

    @Test
    @DisplayName("fail case : 이벤트로 읽을 수 없으면 발행하지 않고 MalformedEventException")
    void 이벤트로_읽을_수_없으면_발행하지_않는다() {
        // act & assert
        assertThatThrownBy(() -> eventProducerService.publishChatSendMessage("{\"channelId\":")).isInstanceOf(MalformedEventException.class);
        assertThatThrownBy(() -> eventProducerService.publishChatReaction("[]")).isInstanceOf(MalformedEventException.class);
        verifyNoInteractions(kafkaTemplate);
    }
}
