package com.distributed.chat.system.kafka.producer.web.producer;

import lombok.RequiredArgsConstructor;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;
import protobuf.EdaChatMessage.ChatMessage;

@Component
@RequiredArgsConstructor
public class EventProducer {

    private final KafkaTemplate<String, byte[]> kafkaTemplate;

    public void publishChatSendMessage(ChatMessage chatMessage) {
        kafkaTemplate.send("chat-send-message", chatMessage.toByteArray());
    }
}
