package com.distributed.chat.system.chatting.task;

import com.distributed.chat.system.chatting.client.CommentClient;
import com.distributed.chat.system.chatting.client.PostClient;
import com.distributed.chat.system.chatting.event.CommentEvent;
import com.distributed.chat.system.chatting.service.NotificationSaveService;
import com.distributed.chat.system.mongodb.collection.Comment;
import com.distributed.chat.system.mongodb.collection.CommentNotification;
import com.distributed.chat.system.mongodb.collection.Notification;
import com.distributed.chat.system.mongodb.collection.Post;
import com.distributed.chat.system.mongodb.enums.NotificationType;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Objects;

@Component
@RequiredArgsConstructor
public class CommentAddTask {

    private final PostClient postClient;

    private final CommentClient commentClient;

    private final NotificationSaveService notificationSaveService;

    public void processEvent(CommentEvent event) {
        // 나 자신 예외
        Post post = postClient.getPost(event.getPostId());
        if (Objects.equals(post.getUserId(), event.getUserId())) {
            return;
        }

        Comment comment = commentClient.getComment(event.getCommentId());

        Notification notification = createNotification(post, comment);
        notificationSaveService.insert(notification);
    }

    private Notification createNotification(Post post, Comment comment) {
        Instant now = Instant.now();

        return new CommentNotification(
                CommentNotification.generateId(),
                post.getUserId(),
                NotificationType.COMMENT,
                comment.getCreatedAt(),
                now,
                now,
                now.plus(90, ChronoUnit.DAYS),
                post.getId(),
                comment.getUserId(),
                comment.getContent(),
                comment.getId());
    }
}
