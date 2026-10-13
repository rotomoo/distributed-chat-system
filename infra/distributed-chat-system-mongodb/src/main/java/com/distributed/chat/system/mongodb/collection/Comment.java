package com.distributed.chat.system.mongodb.collection;

import java.time.Instant;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.bson.types.ObjectId;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Builder
@Document("comment")
public class Comment {

    @Id
    private ObjectId id;
    private ObjectId chatMessageId;
    private String content;
    private Instant createDt;
    private Instant updateDt;
    private Instant deleteDt;
    private Long createUserId;
}
