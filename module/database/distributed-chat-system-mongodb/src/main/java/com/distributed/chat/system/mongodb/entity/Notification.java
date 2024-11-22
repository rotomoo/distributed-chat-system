package com.distributed.chat.system.mongodb.entity;

import com.distributed.chat.system.mongodb.enums.NotificationType;
import java.time.Instant;

public class Notification {

    public String id;
    public Long userId;
    public NotificationType notificationType;
    public Instant createdAt;
    public Instant deletedAt;

    public Notification(String id, Long userId, NotificationType notificationType,
        Instant createdAt, Instant deletedAt) {
        this.id = id;
        this.userId = userId;
        this.notificationType = notificationType;
        this.createdAt = createdAt;
        this.deletedAt = deletedAt;
    }
}
