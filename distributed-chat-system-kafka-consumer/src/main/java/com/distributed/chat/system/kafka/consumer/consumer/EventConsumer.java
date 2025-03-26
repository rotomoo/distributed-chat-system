package com.distributed.chat.system.kafka.consumer.consumer;

import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

@Component
@Slf4j
public class EventConsumer {

    @KafkaListener(topics = "chat-send-message", groupId = "chat-group-1")
    public void consumeChatSendMessage(String message) {
        log.info("[chat-send-message] consumed: {}", message);
    }
}
