package com.distributed.chat.system.mongodb.collection;

import java.time.Instant;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Builder
@Document("chatMessage")
public class ChatMessage {

    @Id
    public String id;
    public Long channelId;
    public String content;
    private Instant eventPublishDt;
    private Instant createDt;
    public Instant updateDt;
    public Instant deleteDt;
    private Long createUserId;
}
