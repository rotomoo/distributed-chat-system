package com.distributed.chat.system.mongodb.entity;

import com.distributed.chat.system.mongodb.enums.NotificationType;
import java.time.Instant;
import lombok.AllArgsConstructor;
import lombok.Getter;
import org.springframework.data.mongodb.core.mapping.Document;

@Getter
@AllArgsConstructor
@Document("notifications")
public abstract class Notification {

    public String id;
    public Long userId;
    public NotificationType type;
    private Instant occurredAt; // 이벤트 발생 시간
    public Instant createdAt;
    public Instant lastUpdatedAt;
    public Instant deletedAt;
}
