package com.distributed.chat.system.chatting.event;

import com.distributed.chat.system.chatting.event.enums.FollowEventType;
import java.time.Instant;
import lombok.Data;

@Data
public class FollowEvent {

    private FollowEventType type;
    private Long postId;
    private Long userId;
    private Instant createdAt;
}
