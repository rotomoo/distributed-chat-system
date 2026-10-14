package com.distributed.chat.system.notification.consumer;

import com.distributed.chat.system.common.kafka.ChatNotificationTopic;
import com.distributed.chat.system.notification.service.NotificationService;
import com.google.protobuf.InvalidProtocolBufferException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;
import protobuf.EdaChatMessage.ChatNotification;

/**
 * kafka-consumer 가 발행한 오프라인 멤버 알림 (key = channelId). 조회 실패는 예외 → 재시도 토픽 (NotificationRetryTopicConfig)
 */
@Component
@Slf4j
@RequiredArgsConstructor
public class ChatNotificationConsumer {

    public static final String GROUP_ID = "notification-group";

    private final NotificationService notificationService;

    @KafkaListener(topics = ChatNotificationTopic.TOPIC, groupId = GROUP_ID)
    public void consume(byte[] payload) {
        ChatNotification notification;
        try {
            notification = ChatNotification.parseFrom(payload);
        } catch (InvalidProtocolBufferException e) {
            // 재시도해도 파싱되지 않으므로 버린다
            log.error("[chat-notification] invalid payload : {}", e.getMessage());
            return;
        }
        notificationService.notify(notification);
    }
}
