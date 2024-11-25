package com.distributed.chat.system.mongodb.repository;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.distributed.chat.system.mongodb.entity.Notification;
import com.distributed.chat.system.mongodb.enums.NotificationType;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootApplication
@SpringBootTest
class NotificationRepositoryTest {

    @Autowired
    private NotificationRepository notificationRepository;

    private final Long userId = 2L;
    private final Instant now = Instant.now();
    private final Instant ninetyDaysAfter = Instant.now().plus(90, ChronoUnit.DAYS);

    @Test
    void test_save() {
        notificationRepository.save(
            new Notification("1", userId, NotificationType.LIKE, now, ninetyDaysAfter));

        Optional<Notification> optionalNotification = notificationRepository.findById("1");

        assertTrue(optionalNotification.isPresent());
    }

    @Test
    void test_find_by_id() {
        String id = "2";

        notificationRepository.save(
            new Notification(id, userId, NotificationType.LIKE, now, ninetyDaysAfter));

        Optional<Notification> optionalNotification = notificationRepository.findById(id);

        Notification notification = optionalNotification.orElseThrow();
        assertEquals(notification.id, id);
        assertEquals(notification.userId, userId);
        assertEquals(notification.createdAt.getEpochSecond(), now.getEpochSecond());
        assertEquals(notification.deletedAt.getEpochSecond(), ninetyDaysAfter.getEpochSecond());
    }

    @Test
    void test_delete_by_id() {
        String id = "3";

        notificationRepository.save(
            new Notification(id, userId, NotificationType.LIKE, now, ninetyDaysAfter));

        notificationRepository.deleteById(id);

        Optional<Notification> optionalNotification = notificationRepository.findById(id);

        assertFalse(optionalNotification.isPresent());
    }
}