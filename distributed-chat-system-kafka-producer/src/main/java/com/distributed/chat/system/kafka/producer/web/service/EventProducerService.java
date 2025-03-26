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
        kafkaTemplate.send("chat-send-message", chatMessage.toByteArray());
    }
}
