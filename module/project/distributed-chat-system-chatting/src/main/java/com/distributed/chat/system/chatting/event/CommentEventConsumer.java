package com.distributed.chat.system.chatting.event;

import com.distributed.chat.system.chatting.event.enums.CommentEventType;
import com.distributed.chat.system.chatting.task.CommentAddTask;
import java.util.function.Consumer;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.stereotype.Component;

@Slf4j
@Component
public class CommentEventConsumer {

    @Autowired
    CommentAddTask commentAddTask;

    @Bean("comment")
    public Consumer<CommentEvent> comment() {
        return event -> {
            if (event.getType().equals(CommentEventType.ADD)) {
                commentAddTask.processEvent(event);
            }
        };
    }
}
