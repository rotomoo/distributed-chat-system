package com.distributed.chat.system.mongodb.entity;

import com.distributed.chat.system.mongodb.enums.NotificationType;
import java.time.Instant;
import lombok.Getter;
import org.springframework.data.annotation.TypeAlias;

@Getter
@TypeAlias("CommentNotification")
public class CommentNotification extends Notification {

    private final Long postId;
    private final Long writerId;
    private final String comment;

    public CommentNotification(String id, Long userId,
        NotificationType type, Instant occurredAt,
        Instant createdAt, Instant lastUpdatedAt, Instant deletedAt, Long postId, Long writerId,
        String comment) {
        super(id, userId, type, occurredAt, createdAt, lastUpdatedAt, deletedAt);
        this.postId = postId;
        this.writerId = writerId;
        this.comment = comment;
    }
}
