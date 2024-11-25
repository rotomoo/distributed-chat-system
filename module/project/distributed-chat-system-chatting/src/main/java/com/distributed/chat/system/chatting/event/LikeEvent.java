package com.distributed.chat.system.chatting.event;

import com.distributed.chat.system.chatting.event.enums.LikeEventType;
import java.time.Instant;
import lombok.Data;

@Data
public class LikeEvent {

    private LikeEventType type;
    private Long postId;
    private Long userId;
    private Instant createdAt;
}
