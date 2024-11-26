package com.distributed.chat.system.mongodb.repository;

import com.distributed.chat.system.mongodb.collection.Notification;
import com.distributed.chat.system.mongodb.enums.NotificationType;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.data.mongodb.repository.Query;

import java.util.Optional;

public interface NotificationRepository extends MongoRepository<Notification, String> {

    Optional<Notification> findById(String id);

    Notification save(Notification notification);

    void deleteById(String id);

    @Query("{ 'type': ?0, 'commentId': ?1 }")
    Optional<Notification> findByTypeAndCommentId(NotificationType notificationType, Long commentId);
}
