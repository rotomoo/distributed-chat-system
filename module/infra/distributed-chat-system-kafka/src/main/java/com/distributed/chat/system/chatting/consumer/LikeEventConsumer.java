package com.distributed.chat.system.chatting.consumer;

import com.distributed.chat.system.chatting.event.LikeEvent;
import com.distributed.chat.system.chatting.event.enums.LikeEventType;
import com.distributed.chat.system.chatting.task.LikeAddTask;
import com.distributed.chat.system.chatting.task.LikeRemoveTask;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Bean;
import org.springframework.stereotype.Component;

import java.util.function.Consumer;

@Slf4j
@Component
@RequiredArgsConstructor
public class LikeEventConsumer {

    private final LikeAddTask likeAddTask;

    private final LikeRemoveTask likeRemoveTask;

    @Bean("like")
    public Consumer<LikeEvent> like() {
        return event -> {
            if (event.getType() == LikeEventType.ADD) {
                likeAddTask.processEvent(event);
            } else if (event.getType() == LikeEventType.REMOVE) {
                likeRemoveTask.processEvent(event);
            }
        };
    }
}
