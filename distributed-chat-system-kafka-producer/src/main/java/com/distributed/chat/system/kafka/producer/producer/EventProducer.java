package com.distributed.chat.system.kafka.producer.producer;

import lombok.RequiredArgsConstructor;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class EventProducer {

    private final KafkaTemplate kafkaTemplate;

    public void publish() {
        kafkaTemplate.send("chat-send-message", "message sent (chat-send-message)");
    }
}
