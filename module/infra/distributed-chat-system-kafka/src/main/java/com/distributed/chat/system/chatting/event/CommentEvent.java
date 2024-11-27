package com.distributed.chat.system.chatting.event;

import com.distributed.chat.system.chatting.event.enums.CommentEventType;
import lombok.Data;

@Data
public class CommentEvent {

    private CommentEventType type;
    private Long postId;
    private Long userId;
    private Long commentId;
}
