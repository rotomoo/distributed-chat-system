package com.distributed.chat.system.kafka.consumer.consumer;

import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Bean;
import org.springframework.stereotype.Component;

import java.util.function.Consumer;

@Slf4j
@Component
public class MessageConsumer {
    @Bean
    public Consumer<String> domainEventString() {
        return message -> {
            System.out.println("Received Message : " + message);
        };
    }
}