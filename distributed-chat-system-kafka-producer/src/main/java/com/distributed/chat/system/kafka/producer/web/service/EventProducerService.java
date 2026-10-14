package com.distributed.chat.system.kafka.producer.web.service;

import lombok.RequiredArgsConstructor;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;
import protobuf.EdaChatMessage.ChatMessage;
import protobuf.EdaChatMessage.ChatReaction;

@Component
@RequiredArgsConstructor
public class EventProducerService {

    public static final String CHAT_SEND_MESSAGE_TOPIC = "chat-send-message";
    public static final String CHAT_REACTION_TOPIC = "chat-reaction";

    private final KafkaTemplate<String, byte[]> kafkaTemplate;

    public void publishChatSendMessage(ChatMessage chatMessage) {
        // 채널 ID 를 키로 사용하여 같은 채널의 메시지는 같은 파티션으로 -> 채널 내 순서 보장
        kafkaTemplate.send(CHAT_SEND_MESSAGE_TOPIC, String.valueOf(chatMessage.getChannelId()), chatMessage.toByteArray());
    }

    public void publishChatReaction(ChatReaction chatReaction) {
        // 메시지와 같이 채널 ID 키 -> 한 채널의 리액션은 한 파티션에서 차례로 처리 (같은 메시지 갱신이 섞이지 않음)
        kafkaTemplate.send(CHAT_REACTION_TOPIC, String.valueOf(chatReaction.getChannelId()), chatReaction.toByteArray());
    }
}
