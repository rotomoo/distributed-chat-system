package com.distributed.chat.system.kafka.consumer.base.kafka;

import static org.assertj.core.api.Assertions.assertThat;

import com.distributed.chat.system.common.event.ChatMessageEvent;
import com.distributed.chat.system.common.event.EventJson;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("[단위] ChatTopics 테스트")
class ChatTopicsUnitTest {

    @Test
    @DisplayName("success case : eventId 가 있으면 그 값을 그대로 쓴다")
    void eventId_가_있으면_그_값을_그대로_쓴다() {
        // arrange
        ChatMessageEvent chatMessage = new ChatMessageEvent("event-1", 5L, 7L, "hello", 1L, List.of());

        // act
        String eventId = ChatTopics.eventIdOf(chatMessage);

        // assert
        assertThat(eventId).isEqualTo("event-1");
    }

    @Test
    @DisplayName("success case : eventId 가 없으면 이벤트 JSON 으로 만든 고정 UUID 다 (같은 이벤트는 같은 값, 다른 이벤트는 다른 값)")
    void eventId_가_없으면_이벤트_JSON_으로_만든_고정_UUID_다() {
        // arrange : 빈 eventId / null eventId / 모르는 필드가 더 있는 JSON 은 모두 같은 이벤트
        ChatMessageEvent chatMessage = new ChatMessageEvent("", 5L, 7L, "hello", 1L, List.of());
        ChatMessageEvent nullEventId = new ChatMessageEvent(null, 5L, 7L, "hello", 1L, null);
        ChatMessageEvent withUnknownField = EventJson.read("{\"sendUserId\":5,\"channelId\":7,\"message\":\"hello\",\"publishDt\":1,\"x\":1}",
            ChatMessageEvent.class);
        ChatMessageEvent other = new ChatMessageEvent("", 5L, 7L, "hello", 2L, List.of());

        // act
        String eventId = ChatTopics.eventIdOf(chatMessage);

        // assert
        assertThat(UUID.fromString(eventId).version()).isEqualTo(3);
        assertThat(ChatTopics.eventIdOf(chatMessage)).isEqualTo(eventId);
        assertThat(ChatTopics.eventIdOf(nullEventId)).isEqualTo(eventId);
        assertThat(ChatTopics.eventIdOf(withUnknownField)).isEqualTo(eventId);
        assertThat(ChatTopics.eventIdOf(other)).isNotEqualTo(eventId);
    }

    @Test
    @DisplayName("success case : 재시도 / DLT 토픽 이름에서 원래 토픽을 찾는다")
    void 재시도_DLT_토픽_이름에서_원래_토픽을_찾는다() {
        // act & assert
        assertThat(ChatTopics.mainTopicOf("chat-send-message-retry-1000")).isEqualTo("chat-send-message");
        assertThat(ChatTopics.mainTopicOf("chat-reaction-dlt")).isEqualTo("chat-reaction");
        assertThat(ChatTopics.mainTopicOf("chat-reaction")).isEqualTo("chat-reaction");
        assertThat(ChatTopics.mainTopicOf("other")).isEqualTo("other");
    }
}
