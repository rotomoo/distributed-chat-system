package com.distributed.chat.system.chatting.task;

import com.distributed.chat.system.chatting.client.PostClient;
import com.distributed.chat.system.chatting.event.LikeEvent;
import com.distributed.chat.system.chatting.service.NotificationGetService;
import com.distributed.chat.system.chatting.service.NotificationSaveService;
import com.distributed.chat.system.mongodb.collection.LikeNotification;
import com.distributed.chat.system.mongodb.collection.Notification;
import com.distributed.chat.system.mongodb.collection.Post;
import com.distributed.chat.system.mongodb.enums.NotificationType;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Optional;


@Slf4j
@Component
@RequiredArgsConstructor
public class LikeAddTask {

    private final PostClient postClient;

    private final NotificationGetService getService;

    private final NotificationSaveService saveService;

    public void processEvent(LikeEvent event) {
        Post post = postClient.getPost(event.getPostId());
        if (post == null) {
            log.error("Post is null with postId:{}", event.getPostId());
            return;
        }

        if (post.getUserId() == event.getUserId()) {
            return;
        }

        saveService.upsert(createOrUpdateNotification(post, event));
    }

    private Notification createOrUpdateNotification(Post post, LikeEvent event) {
        Optional<Notification> optionalNotification = getService.getNotificationByTypeAndPostId(NotificationType.LIKE, post.getId());

        Instant now = Instant.now();
        Instant retention = now.plus(90, ChronoUnit.DAYS);

        if (optionalNotification.isPresent()) {
            return updateNotification((LikeNotification) optionalNotification.get(), event, now, retention);
        } else {
            return createNotification(post, event, now, retention);
        }
    }

    private Notification updateNotification(LikeNotification notification, LikeEvent event, Instant now, Instant retention) {
        notification.addLiker(event.getUserId(), event.getCreatedAt(), now, retention);
        return notification;
    }

    private Notification createNotification(Post post, LikeEvent event, Instant now, Instant retention) {
        return new LikeNotification(
                LikeNotification.generateId(),
                post.getUserId(),
                NotificationType.LIKE,
                event.getCreatedAt(),
                now,
                now,
                retention,
                post.getId(),
                List.of(event.getUserId())
        );
    }
}
