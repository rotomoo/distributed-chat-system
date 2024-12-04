package com.distributed.chat.system.kafka.producer.producer;

import lombok.RequiredArgsConstructor;
import org.springframework.cloud.stream.function.StreamBridge;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class MessageProducer {

    private final StreamBridge streamBridge;

    public void sendMessage(String payload) {
        streamBridge.send("domainEventString-out-0", payload);
    }
}