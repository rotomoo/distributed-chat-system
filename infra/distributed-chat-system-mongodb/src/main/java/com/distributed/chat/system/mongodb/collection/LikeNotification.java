package com.distributed.chat.system.mongodb.collection;

import com.distributed.chat.system.mongodb.enums.NotificationType;
import lombok.Getter;
import org.springframework.data.annotation.TypeAlias;

import java.time.Instant;
import java.util.List;

@Getter
@TypeAlias("LikeNotification")
public class LikeNotification extends Notification {
    private final long postId;
    private final List<Long> likerIds;

    public LikeNotification(String id, long userId, NotificationType type, Instant occurredAt, Instant createdAt,
                            Instant lastUpdatedAt, Instant deletedAt, long postId, List<Long> likerIds) {
        super(id, userId, type, occurredAt, createdAt, lastUpdatedAt, deletedAt);
        this.postId = postId;
        this.likerIds = likerIds;
    }

    public void addLiker(long likerId, Instant occurredAt, Instant now, Instant retention) {
        this.likerIds.add(likerId);
        this.changeOccurredAt(occurredAt);
        this.changeLastUpdatedAt(now);
        this.changeDeletedAt(retention);
    }

    public void removeLiker(long userId, Instant now) {
        this.likerIds.remove(userId);
        this.changeLastUpdatedAt(now);
    }
}
