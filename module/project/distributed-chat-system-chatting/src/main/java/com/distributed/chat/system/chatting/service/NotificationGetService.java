package com.distributed.chat.system.chatting.service;

import com.distributed.chat.system.mongodb.collection.Notification;
import com.distributed.chat.system.mongodb.enums.NotificationType;
import com.distributed.chat.system.mongodb.repository.NotificationRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.Optional;

@Service
@Slf4j
@RequiredArgsConstructor
public class NotificationGetService {

    private final NotificationRepository notificationRepository;

    public Optional<Notification> getNotification(NotificationType notificationType, Long commentId) {
        return notificationRepository.findByTypeAndCommentId(notificationType, commentId);
    }
}
