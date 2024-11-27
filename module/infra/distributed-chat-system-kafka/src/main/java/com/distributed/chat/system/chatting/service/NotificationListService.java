package com.distributed.chat.system.chatting.service;

import com.distributed.chat.system.chatting.service.dto.GetUserNotificationsByPivotResult;
import com.distributed.chat.system.mongodb.collection.Notification;
import com.distributed.chat.system.mongodb.repository.NotificationRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Slice;
import org.springframework.stereotype.Service;

import java.time.Instant;

@Service
@Slf4j
@RequiredArgsConstructor
public class NotificationListService {

    private final NotificationRepository notificationRepository;

    public GetUserNotificationsByPivotResult getUserNotificationsByPivot(long userId, Instant occurredAt) {
        Slice<Notification> result;

        if (occurredAt == null) {
            result = notificationRepository.findAllByUserIdOrderByOccurredAtDesc(userId, PageRequest.of(0, PAGE_SIZE));
        } else {
            result = notificationRepository.findAllByUserIdAndOccurredAtLessThanOrderByOccurredAtDesc(userId, occurredAt, PageRequest.of(0, PAGE_SIZE));
        }

        return new GetUserNotificationsByPivotResult(
                result.toList(),
                result.hasNext()
        );
    }

    private static final int PAGE_SIZE = 20;
}
