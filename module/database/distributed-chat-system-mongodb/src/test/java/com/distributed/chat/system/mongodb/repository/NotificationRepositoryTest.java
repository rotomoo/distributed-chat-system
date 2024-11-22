package com.distributed.chat.system.mongodb.repository;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import org.junit.Test;
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
        notificationRepository.save()
    }
}