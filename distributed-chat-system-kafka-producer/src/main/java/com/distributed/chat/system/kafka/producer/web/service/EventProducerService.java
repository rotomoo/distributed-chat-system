package com.distributed.chat.system.kafka.producer.web.service;

import com.distributed.chat.system.common.event.ChatMessageEvent;
import com.distributed.chat.system.common.event.ChatNotificationEvent;
import com.distributed.chat.system.common.event.ChatReactionEvent;
import com.distributed.chat.system.common.event.EventJson;
import com.distributed.chat.system.common.kafka.ChatNotificationTopic;
import lombok.RequiredArgsConstructor;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

/**
 * 채팅 서버(메시지 / 리액션)와 컨슈머(알림)가 보낸 이벤트 JSON 을 읽어서 키(channelId)만 꺼내고, 값은 받은 문자열 그대로 발행한다.
 * 다시 직렬화하지 않으므로 이 서버가 모르는 새 필드도 컨슈머까지 그대로 간다 (호환 규칙 {@link EventJson})
 */
@Component
@RequiredArgsConstructor
public class EventProducerService {

    public static final String CHAT_SEND_MESSAGE_TOPIC = "chat-send-message";
    public static final String CHAT_REACTION_TOPIC = "chat-reaction";
    public static final String CHAT_NOTIFICATION_TOPIC = ChatNotificationTopic.TOPIC;

    private final KafkaTemplate<String, String> kafkaTemplate;

    /**
     * @param payload {@link ChatMessageEvent} JSON
     * @throws com.distributed.chat.system.common.event.MalformedEventException 메시지 이벤트로 읽을 수 없음 (발행하지 않는다)
     */
    public void publishChatSendMessage(String payload) {
        ChatMessageEvent chatMessage = EventJson.read(payload, ChatMessageEvent.class);
        // 채널 ID 를 키로 사용하여 같은 채널의 메시지는 같은 파티션으로 -> 채널 내 순서 보장
        kafkaTemplate.send(CHAT_SEND_MESSAGE_TOPIC, String.valueOf(chatMessage.channelId()), payload);
    }

    /**
     * @param payload {@link ChatReactionEvent} JSON
     * @throws com.distributed.chat.system.common.event.MalformedEventException 리액션 이벤트로 읽을 수 없음 (발행하지 않는다)
     */
    public void publishChatReaction(String payload) {
        ChatReactionEvent chatReaction = EventJson.read(payload, ChatReactionEvent.class);
        // 메시지와 같이 채널 ID 키 -> 한 채널의 리액션은 한 파티션에서 차례로 처리 (같은 메시지 갱신이 섞이지 않음)
        kafkaTemplate.send(CHAT_REACTION_TOPIC, String.valueOf(chatReaction.channelId()), payload);
    }

    /**
     * @param payload {@link ChatNotificationEvent} JSON
     * @throws com.distributed.chat.system.common.event.MalformedEventException 알림 이벤트로 읽을 수 없음 (발행하지 않는다)
     */
    public void publishChatNotification(String payload) {
        ChatNotificationEvent chatNotification = EventJson.read(payload, ChatNotificationEvent.class);
        // 같은 채널 알림은 한 파티션에서 차례로 (notification-consumer 의 30초 제한이 채널 단위)
        kafkaTemplate.send(CHAT_NOTIFICATION_TOPIC, String.valueOf(chatNotification.channelId()), payload);
    }
}
