package com.distributed.chat.system.mongodb;

import com.distributed.chat.system.mongodb.entity.Notification;
import com.distributed.chat.system.mongodb.repository.NotificationRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component
@Slf4j
public class NotificationSaveService {

    @Autowired
    private NotificationRepository notificationRepository;

    public void insert(Notification notification) {
        Notification insert = notificationRepository.insert(notification);
        log.info("inserted: {}" + insert);
    }
}
