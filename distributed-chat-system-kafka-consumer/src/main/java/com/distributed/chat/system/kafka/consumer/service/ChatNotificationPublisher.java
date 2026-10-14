package com.distributed.chat.system.kafka.consumer.service;

import com.distributed.chat.system.common.kafka.ChatNotificationTopic;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;
import protobuf.EdaChatMessage.ChatMessage;
import protobuf.EdaChatMessage.ChatNotification;

/**
 * 오프라인 멤버에게 웹 푸시를 보내 달라고 notification 서버에 넘긴다 (토픽 chat-notification, key = channelId).
 * <p>
 * <b>발행 실패는 레코드를 실패시키지 않는다</b> (예외를 밖으로 던지지 않고 WARN 로그만 남긴다)
 * <ul>
 *   <li>이 시점에 메시지는 이미 저장됐다. 레코드를 실패시키면 재시도 토픽이 실시간 전달까지 다시 하게 되는데, 그 이유가 "알림 하나" 라면
 *       얻는 것보다 잃는 것(중복 전달, 파티션 지연)이 크다</li>
 *   <li>알림은 유실을 허용한다 (설계 계약). 놓친 메시지는 앱을 열면 MongoDB 에서 받는다</li>
 *   <li>보내기는 비동기 (KafkaTemplate 버퍼에 넣고 바로 돌아온다). 컨슈머 스레드가 알림 브로커 응답을 기다리지 않는다</li>
 * </ul>
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class ChatNotificationPublisher {

    private final KafkaTemplate<String, byte[]> kafkaTemplate;

    /**
     * @param offlineUserIds 오프라인 멤버 ({@link ChatReceivers#offline()}). 보낸 사람은 여기서 뺀다
     * @return 발행을 시도한 이벤트 수 (받을 사람이 없으면 0)
     */
    public int publish(String messageId, ChatMessage chatMessage, int attachmentCount, List<Long> offlineUserIds) {
        List<Long> receivers = offlineUserIds.stream()
            .filter(userId -> userId != chatMessage.getSendUserId())
            .distinct()
            .toList();
        if (receivers.isEmpty()) {
            return 0;
        }

        String key = String.valueOf(chatMessage.getChannelId());
        String preview = ChatNotificationTopic.preview(chatMessage.getMessage());
        List<List<Long>> chunks = ChatMessageDeliveryService.chunks(receivers, ChatNotificationTopic.MAX_RECEIVERS);
        for (List<Long> chunk : chunks) {
            ChatNotification notification = ChatNotification.newBuilder()
                .setMessageId(messageId)
                .setChannelId(chatMessage.getChannelId())
                .setSenderId(chatMessage.getSendUserId())
                .setPreview(preview)
                .setAttachmentCount(attachmentCount)
                .setPublishDt(chatMessage.getPublishDt())
                .addAllReceiverUserIds(chunk)
                .build();
            send(key, notification);
        }
        return chunks.size();
    }

    private void send(String key, ChatNotification notification) {
        try {
            kafkaTemplate.send(ChatNotificationTopic.TOPIC, key, notification.toByteArray())
                .whenComplete((result, error) -> {
                    if (error != null) {
                        warnDropped(notification, error);
                    }
                });
        } catch (Exception e) {
            // 직렬화 / 메타데이터 대기 초과 등 send 자체가 던진 경우
            warnDropped(notification, e);
        }
    }

    private static void warnDropped(ChatNotification notification, Throwable error) {
        log.warn("[chat-notification] publish fail, notification dropped messageId : {}, channelId : {}, receivers : {}, error : {}",
            notification.getMessageId(), notification.getChannelId(), notification.getReceiverUserIdsCount(), error.getMessage());
    }
}
