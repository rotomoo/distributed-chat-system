package com.distributed.chat.system.chatting.task;

import com.distributed.chat.system.chatting.client.PostClient;
import com.distributed.chat.system.chatting.event.CommentEvent;
import com.distributed.chat.system.chatting.service.NotificationGetService;
import com.distributed.chat.system.chatting.service.NotificationRemoveService;
import com.distributed.chat.system.mongodb.collection.Post;
import com.distributed.chat.system.mongodb.enums.NotificationType;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Objects;

@Component
@Slf4j
@RequiredArgsConstructor
public class CommentRemoveTask {

    private final PostClient postClient;
    
    private final NotificationGetService notificationGetService;

    private final NotificationRemoveService notificationRemoveService;

    public void processEvent(CommentEvent event) {
        // 나 자신 예외
        Post post = postClient.getPost(event.getPostId());
        if (Objects.equals(post.getUserId(), event.getUserId())) {
            return;
        }

        notificationGetService.getNotification(NotificationType.COMMENT, event.getCommentId())
                .ifPresentOrElse(
                        notification -> notificationRemoveService.deleteById(notification.getId()),
                        () -> log.error("notification not found")
                );
    }
}
