package com.distributed.chat.system.kafka.producer.web.service;

import lombok.RequiredArgsConstructor;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;
import protobuf.EdaChatMessage.ChatMessage;

@Component
@RequiredArgsConstructor
public class EventProducerService {

    private final KafkaTemplate<String, byte[]> kafkaTemplate;

    public void publishChatSendMessage(ChatMessage chatMessage) {
        // 채널 ID 를 키로 사용하여 같은 채널의 메시지는 같은 파티션으로 -> 채널 내 순서 보장
        kafkaTemplate.send("chat-send-message", String.valueOf(chatMessage.getChannelId()),
            chatMessage.toByteArray());
    }
}
