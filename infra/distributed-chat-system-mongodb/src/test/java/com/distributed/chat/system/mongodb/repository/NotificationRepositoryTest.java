package com.distributed.chat.system.mongodb.repository;

import com.distributed.chat.system.mongodb.collection.CommentNotification;
import com.distributed.chat.system.mongodb.collection.Notification;
import com.distributed.chat.system.mongodb.enums.NotificationType;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.test.context.SpringBootTest;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

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
                new CommentNotification("1", userId, NotificationType.LIKE, now.plusSeconds(1), now,
                        now, ninetyDaysAfter, 1L, 1L, "test", 1L));

        Optional<Notification> optionalNotification = notificationRepository.findById("1");

        assertTrue(optionalNotification.isPresent());
    }

    @Test
    void test_find_by_id() {
        String id = "2";

        notificationRepository.save(
                new CommentNotification(id, userId, NotificationType.LIKE, now.plusSeconds(1), now,
                        now, ninetyDaysAfter, 1L, 1L, "test", 1L));

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
                new CommentNotification("1", userId, NotificationType.LIKE, now.plusSeconds(1), now,
                        now, ninetyDaysAfter, 1L, 1L, "test", 1L));

        notificationRepository.deleteById(id);

        Optional<Notification> optionalNotification = notificationRepository.findById(id);

        assertFalse(optionalNotification.isPresent());
    }
}