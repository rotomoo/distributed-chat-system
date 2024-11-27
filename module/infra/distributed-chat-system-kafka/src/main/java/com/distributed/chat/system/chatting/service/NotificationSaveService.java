package com.distributed.chat.system.chatting.service;

import com.distributed.chat.system.mongodb.collection.Notification;
import com.distributed.chat.system.mongodb.repository.NotificationRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Service
@Slf4j
@RequiredArgsConstructor
public class NotificationSaveService {

    private final NotificationRepository notificationRepository;

    public void insert(Notification notification) {
        Notification result = notificationRepository.insert(notification);
        log.info("inserted: {}", result);
    }

    public void upsert(Notification notification) {
        Notification result = notificationRepository.save(notification);
        log.info("upserted: {}", result);
    }
}
