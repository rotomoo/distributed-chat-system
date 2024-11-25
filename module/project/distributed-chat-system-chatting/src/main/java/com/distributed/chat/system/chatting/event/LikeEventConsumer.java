package com.distributed.chat.system.chatting.event;

import java.util.function.Consumer;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Bean;
import org.springframework.stereotype.Component;

@Slf4j
@Component
public class LikeEventConsumer {

    @Bean("like")
    public Consumer<LikeEvent> like() {
        return event -> log.info(event.toString());
    }
}
