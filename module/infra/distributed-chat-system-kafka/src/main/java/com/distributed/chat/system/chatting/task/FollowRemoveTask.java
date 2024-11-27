package com.distributed.chat.system.chatting.task;

import com.distributed.chat.system.chatting.event.FollowEvent;
import com.distributed.chat.system.chatting.service.NotificationGetService;
import com.distributed.chat.system.chatting.service.NotificationRemoveService;
import com.distributed.chat.system.mongodb.enums.NotificationType;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class FollowRemoveTask {

    private final NotificationGetService notificationGetService;

    private final NotificationRemoveService notificationRemoveService;

    public void processEvent(FollowEvent event) {
        notificationGetService.getNotificationByTypeAndUserIdAndFollowerId(NotificationType.FOLLOW, event.getTargetUserId(),
                        event.getUserId())
                .ifPresent(
                        notification -> notificationRemoveService.deleteById(notification.getId())
                );
    }
}
