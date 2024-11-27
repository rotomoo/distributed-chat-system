package com.distributed.chat.system.chatting.consumer;

import com.distributed.chat.system.chatting.event.FollowEvent;
import com.distributed.chat.system.chatting.event.enums.FollowEventType;
import com.distributed.chat.system.chatting.task.FollowAddTask;
import com.distributed.chat.system.chatting.task.FollowRemoveTask;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Bean;
import org.springframework.stereotype.Component;

import java.util.function.Consumer;

@Slf4j
@Component
public class FollowEventConsumer {

    private final FollowAddTask followAddTask;

    private final FollowRemoveTask followRemoveTask;

    public FollowEventConsumer(FollowAddTask followAddTask, FollowRemoveTask followRemoveTask) {
        this.followAddTask = followAddTask;
        this.followRemoveTask = followRemoveTask;
    }

    @Bean("follow")
    public Consumer<FollowEvent> follow() {
        return event -> {
            if (event.getType() == FollowEventType.ADD) {
                followAddTask.processEvent(event);
            } else if (event.getType() == FollowEventType.REMOVE) {
                followRemoveTask.processEvent(event);
            }
        };
    }
}
