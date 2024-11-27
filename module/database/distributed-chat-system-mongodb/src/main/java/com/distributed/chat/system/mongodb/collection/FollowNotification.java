package com.distributed.chat.system.mongodb.collection;

import com.distributed.chat.system.mongodb.enums.NotificationType;
import lombok.Getter;
import org.springframework.data.annotation.TypeAlias;

import java.time.Instant;

@Getter
@TypeAlias("CommentNotification")
public class FollowNotification extends Notification {

    private final long followerId;

    public FollowNotification(String id, Long userId,
                              NotificationType type, Instant occurredAt,
                              Instant createdAt, Instant lastUpdatedAt, Instant deletedAt, long followerId) {
        super(id, userId, type, occurredAt, createdAt, lastUpdatedAt, deletedAt);
        this.followerId = followerId;
    }
}
