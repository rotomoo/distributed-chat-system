package com.distributed.chat.system.notification.consumer.consumer;

import com.distributed.chat.system.common.event.ChatNotificationEvent;
import com.distributed.chat.system.common.event.EventJson;
import com.distributed.chat.system.common.kafka.ChatNotificationTopic;
import com.distributed.chat.system.notification.consumer.service.NotificationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

/**
 * kafka-producer 가 발행한 오프라인 멤버 알림 (message-consumer 가 요청, key = channelId, 값 = 이벤트 JSON). 조회 실패는 예외 → 재시도 토픽 (NotificationRetryTopicConfig).
 * 읽을 수 없는 값은 {@link com.distributed.chat.system.common.event.MalformedEventException} → 재시도 없이 바로 DLT (로그만 남고 버려진다)
 */
@Component
@Slf4j
@RequiredArgsConstructor
public class ChatNotificationConsumer {

    public static final String GROUP_ID = "notification-group";

    private final NotificationService notificationService;

    @KafkaListener(topics = ChatNotificationTopic.TOPIC, groupId = GROUP_ID)
    public void consume(String payload) {
        notificationService.notify(EventJson.read(payload, ChatNotificationEvent.class));
    }
}
