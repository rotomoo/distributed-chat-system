package com.distributed.chat.system.chatting.event;

import java.util.function.Consumer;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Bean;
import org.springframework.stereotype.Component;

@Slf4j
@Component
public class FollowEventConsumer {

    @Bean("follow")
    public Consumer<LikeEvent> follow() {
        return event -> log.info(event.toString());
    }
}
