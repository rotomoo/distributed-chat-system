package com.distributed.chat.system.mongodb.collection;

import com.distributed.chat.system.mongodb.enums.NotificationType;
import lombok.AllArgsConstructor;
import lombok.Getter;
import org.bson.types.ObjectId;
import org.springframework.data.mongodb.core.mapping.Document;
import org.springframework.data.mongodb.core.mapping.Field;
import org.springframework.data.mongodb.core.mapping.FieldType;

import java.time.Instant;

@Getter
@AllArgsConstructor
@Document("notifications")
public abstract class Notification {

    @Field(targetType = FieldType.STRING)
    public String id;
    public Long userId;
    public NotificationType type;
    private Instant occurredAt; // 이벤트 발생 시간
    public Instant createdAt;
    public Instant lastUpdatedAt;
    public Instant deletedAt;

    public void changeOccurredAt(Instant occurredAt) {
        this.occurredAt = occurredAt;
    }

    public void changeDeletedAt(Instant deletedAt) {
        this.deletedAt = deletedAt;
    }

    public void changeLastUpdatedAt(Instant lastUpdatedAt) {
        this.lastUpdatedAt = lastUpdatedAt;
    }

    public static String generateId() {
        return new ObjectId().toString();
    }
}
