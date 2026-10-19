package com.distributed.chat.system.message.consumer.base.kafka;

import com.distributed.chat.system.common.event.ChatMessageEvent;
import com.distributed.chat.system.common.event.EventJson;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.UUID;

/**
 * 컨슈머가 읽는 토픽과 이벤트 식별
 */
public final class ChatTopics {

    public static final String CHAT_SEND_MESSAGE = "chat-send-message";
    public static final String CHAT_REACTION = "chat-reaction";
    public static final List<String> ALL = List.of(CHAT_SEND_MESSAGE, CHAT_REACTION);

    private ChatTopics() {
    }

    /**
     * 메시지 이벤트 ID. 채팅 서버는 항상 넣지만, 빠진 이벤트(손으로 다시 발행한 JSON 등)는 이벤트 JSON 으로 만든 고정 UUID 를 쓴다.
     * 같은 이벤트면 재시도 / DLT 재발행에도 같은 값이라 한 번만 저장되고, 서로 다른 이벤트가 빈 eventId 하나로 합쳐지지 않는다
     */
    public static String eventIdOf(ChatMessageEvent chatMessage) {
        return chatMessage.eventId().isBlank()
            ? UUID.nameUUIDFromBytes(EventJson.write(chatMessage).getBytes(StandardCharsets.UTF_8)).toString()
            : chatMessage.eventId();
    }

    /**
     * 재시도 / DLT 토픽 이름에서 원래 토픽을 찾는다 (예: chat-send-message-retry-1000 → chat-send-message)
     */
    public static String mainTopicOf(String topic) {
        return ALL.stream().filter(main -> topic.equals(main) || topic.startsWith(main + "-")).findFirst().orElse(topic);
    }
}
