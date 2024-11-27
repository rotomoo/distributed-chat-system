package com.distributed.chat.system.chatting.task;

import com.distributed.chat.system.chatting.event.FollowEvent;
import com.distributed.chat.system.chatting.service.NotificationSaveService;
import com.distributed.chat.system.mongodb.collection.FollowNotification;
import com.distributed.chat.system.mongodb.enums.NotificationType;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.time.temporal.ChronoUnit;

@Slf4j
@Component
@RequiredArgsConstructor
public class FollowAddTask {

    private final NotificationSaveService saveService;


    public void processEvent(FollowEvent event) {
        if (event.getTargetUserId() == event.getUserId()) {
            log.error("targetUserId and userId cannot be the same");
            return;
        }

        saveService.insert(createFollowNotification(event));
    }

    private FollowNotification createFollowNotification(FollowEvent event) {
        Instant now = Instant.now();

        return new FollowNotification(
                FollowNotification.generateId(),
                event.getTargetUserId(),
                NotificationType.FOLLOW,
                event.getCreatedAt(),
                now,
                now,
                now.plus(90, ChronoUnit.DAYS),
                event.getUserId()
        );
    }
}
