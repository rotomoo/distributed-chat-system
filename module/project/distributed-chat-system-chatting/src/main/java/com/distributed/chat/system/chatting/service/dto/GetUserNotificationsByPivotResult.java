package com.distributed.chat.system.chatting.service.dto;

import com.distributed.chat.system.mongodb.collection.Notification;
import lombok.AllArgsConstructor;
import lombok.Getter;

import java.util.List;

@Getter
@AllArgsConstructor
public class GetUserNotificationsByPivotResult {
    private List<Notification> notifications;
    private boolean hasNext;
}
