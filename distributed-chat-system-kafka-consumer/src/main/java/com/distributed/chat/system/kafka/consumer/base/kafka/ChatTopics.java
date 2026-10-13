package com.distributed.chat.system.kafka.consumer.base.kafka;

import java.util.List;
import java.util.UUID;
import protobuf.EdaChatMessage.ChatMessage;

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
     * 메시지 이벤트 ID. 예전 채팅 서버(event_id 없음)가 보낸 것은 본문으로 만든 고정 UUID 를 쓴다 (재시도해도 같은 값)
     */
    public static String eventIdOf(ChatMessage chatMessage) {
        return chatMessage.getEventId().isBlank()
            ? UUID.nameUUIDFromBytes(chatMessage.toByteArray()).toString()
            : chatMessage.getEventId();
    }

    /**
     * 재시도 / DLT 토픽 이름에서 원래 토픽을 찾는다 (예: chat-send-message-retry-1000 → chat-send-message)
     */
    public static String mainTopicOf(String topic) {
        return ALL.stream().filter(main -> topic.equals(main) || topic.startsWith(main + "-")).findFirst().orElse(topic);
    }
}
