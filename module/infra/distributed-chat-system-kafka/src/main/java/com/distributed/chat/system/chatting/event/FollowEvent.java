package com.distributed.chat.system.chatting.event;

import com.distributed.chat.system.chatting.event.enums.FollowEventType;
import lombok.Data;

import java.time.Instant;

@Data
public class FollowEvent {

    private FollowEventType type;
    private Long userId;
    private Long targetUserId;
    private Instant createdAt;
}
