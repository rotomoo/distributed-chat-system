package com.distributed.chat.system.chatting.service;

import com.distributed.chat.system.mongodb.repository.NotificationRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Service
@Slf4j
@RequiredArgsConstructor
public class NotificationRemoveService {

    private final NotificationRepository notificationRepository;

    public void deleteById(String id) {
        notificationRepository.deleteById(id);
        log.info("deleted: {}", id);
    }
}
