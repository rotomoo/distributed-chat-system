package com.distributed.chat.system.chatting.consumer;

import com.distributed.chat.system.chatting.event.CommentEvent;
import com.distributed.chat.system.chatting.event.enums.CommentEventType;
import com.distributed.chat.system.chatting.task.CommentAddTask;
import com.distributed.chat.system.chatting.task.CommentRemoveTask;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.stereotype.Component;

import java.util.function.Consumer;

@Slf4j
@Component
public class CommentEventConsumer {

    @Autowired
    CommentAddTask commentAddTask;

    @Autowired
    CommentRemoveTask commentRemoveTask;

    @Bean("comment")
    public Consumer<CommentEvent> comment() {
        return event -> {
            if (event.getType().equals(CommentEventType.ADD)) {
                commentAddTask.processEvent(event);
            } else if (event.getType().equals(CommentEventType.REMOVE)) {
                commentRemoveTask.processEvent(event);
            }
        };
    }
}
