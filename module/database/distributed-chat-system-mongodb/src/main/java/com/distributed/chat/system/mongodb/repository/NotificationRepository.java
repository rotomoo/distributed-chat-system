package com.distributed.chat.system.mongodb.repository;

import com.distributed.chat.system.mongodb.entity.Notification;
import java.util.Optional;
import org.springframework.data.mongodb.repository.MongoRepository;

public interface NotificationRepository extends MongoRepository<Notification, String> {

    Optional<Notification> findById(String id);

    Notification save(Notification notification);

    void deleteById(String id);
}
