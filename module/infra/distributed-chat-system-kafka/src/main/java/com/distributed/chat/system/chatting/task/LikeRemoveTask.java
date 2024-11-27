package com.distributed.chat.system.chatting.task;


import com.distributed.chat.system.chatting.event.LikeEvent;
import com.distributed.chat.system.chatting.service.NotificationGetService;
import com.distributed.chat.system.chatting.service.NotificationRemoveService;
import com.distributed.chat.system.chatting.service.NotificationSaveService;
import com.distributed.chat.system.mongodb.collection.LikeNotification;
import com.distributed.chat.system.mongodb.collection.Notification;
import com.distributed.chat.system.mongodb.enums.NotificationType;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.Optional;


@Slf4j
@Component
@RequiredArgsConstructor
public class LikeRemoveTask {

    private final NotificationGetService getService;

    private final NotificationRemoveService removeService;

    private final NotificationSaveService saveService;

    public void processEvent(LikeEvent event) {
        Optional<Notification> optionalNotification = getService.getNotificationByTypeAndPostId(NotificationType.LIKE, event.getPostId());
        if (optionalNotification.isEmpty()) {
            log.error("No notification with postId: {}", event.getPostId());
            return;
        }

        LikeNotification notification = (LikeNotification) optionalNotification.get();
        removeLikerAndUpdateNotification(notification, event);
    }

    private void removeLikerAndUpdateNotification(LikeNotification notification, LikeEvent event) {
        notification.removeLiker(event.getUserId(), Instant.now());

        if (notification.getLikerIds().isEmpty()) {
            removeService.deleteById(notification.getId());
        } else {
            saveService.upsert(notification);
        }
    }
}
