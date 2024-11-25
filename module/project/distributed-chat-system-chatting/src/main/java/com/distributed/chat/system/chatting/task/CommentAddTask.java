package com.distributed.chat.system.chatting.task;

import com.distributed.chat.system.chatting.event.CommentEvent;
import com.distributed.chat.system.mongodb.Comment;
import com.distributed.chat.system.mongodb.CommentClient;
import com.distributed.chat.system.mongodb.NotificationIdGenerator;
import com.distributed.chat.system.mongodb.NotificationSaveService;
import com.distributed.chat.system.mongodb.Post;
import com.distributed.chat.system.mongodb.PostClient;
import com.distributed.chat.system.mongodb.entity.CommentNotification;
import com.distributed.chat.system.mongodb.entity.Notification;
import com.distributed.chat.system.mongodb.enums.NotificationType;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Objects;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component
public class CommentAddTask {

    @Autowired
    PostClient postClient;

    @Autowired
    CommentClient commentClient;

    @Autowired
    NotificationSaveService notificationSaveService;

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
            NotificationIdGenerator.generate(),
            post.getUserId(),
            NotificationType.COMMENT,
            comment.getCreatedAt(),
            now,
            now,
            now.plus(90, ChronoUnit.DAYS),
            post.getId(),
            comment.getUserId(),
            comment.getContent()
        );
    }
}
