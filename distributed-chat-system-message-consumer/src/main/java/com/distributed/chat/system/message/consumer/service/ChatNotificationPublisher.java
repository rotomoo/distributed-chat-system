package com.distributed.chat.system.message.consumer.service;

import com.distributed.chat.system.common.event.ChatMessageEvent;
import com.distributed.chat.system.common.event.ChatNotificationEvent;
import com.distributed.chat.system.common.kafka.ChatNotificationTopic;
import com.distributed.chat.system.message.consumer.feign.KafkaProducerClient;
import java.util.List;
import java.util.concurrent.Executor;
import java.util.concurrent.RejectedExecutionException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;

/**
 * 오프라인 멤버에게 웹 푸시를 보내 달라고 notification-consumer 에 넘긴다.
 * 발행은 kafka-producer 가 한다 (HTTP, 토픽 chat-notification, key = channelId). 컨슈머는 소비만 한다
 * <p>
 * <b>발행 실패는 레코드를 실패시키지 않는다</b> (예외를 밖으로 던지지 않고 WARN 로그만 남긴다)
 * <ul>
 *   <li>이 시점에 메시지는 이미 저장됐다. 레코드를 실패시키면 재시도 토픽이 실시간 전달까지 다시 하게 되는데, 그 이유가 "알림 하나" 라면
 *       얻는 것보다 잃는 것(중복 전달, 파티션 지연)이 크다</li>
 *   <li>알림은 유실을 허용한다 (설계 계약). 놓친 메시지는 앱을 열면 MongoDB 에서 받는다</li>
 *   <li>보내기는 비동기 (알림 전용 스레드 대기열에 넣고 바로 돌아온다). 컨슈머 스레드가 kafka-producer 응답을 기다리지 않는다.
 *       대기열이 가득 차면 넣지 못한 알림은 버린다 ({@link NotificationExecutorConfig})</li>
 * </ul>
 */
@Service
@Slf4j
public class ChatNotificationPublisher {

    private final KafkaProducerClient kafkaProducerClient;
    private final Executor chatNotificationExecutor;

    public ChatNotificationPublisher(KafkaProducerClient kafkaProducerClient,
        @Qualifier(NotificationExecutorConfig.EXECUTOR) Executor chatNotificationExecutor) {
        this.kafkaProducerClient = kafkaProducerClient;
        this.chatNotificationExecutor = chatNotificationExecutor;
    }

    /**
     * @param offlineUserIds 오프라인 멤버 ({@link ChatReceivers#offline()}). 보낸 사람은 여기서 뺀다
     * @return 발행을 시도한 이벤트 수 (받을 사람이 없으면 0)
     */
    public int publish(String messageId, ChatMessageEvent chatMessage, int attachmentCount, List<Long> offlineUserIds) {
        List<Long> receivers = offlineUserIds.stream()
            .filter(userId -> userId != chatMessage.sendUserId())
            .distinct()
            .toList();
        if (receivers.isEmpty()) {
            return 0;
        }

        String preview = ChatNotificationTopic.preview(chatMessage.message());
        List<List<Long>> chunks = ChatMessageDeliveryService.chunks(receivers, ChatNotificationTopic.MAX_RECEIVERS);
        for (List<Long> chunk : chunks) {
            send(new ChatNotificationEvent(messageId, chatMessage.channelId(), chatMessage.sendUserId(), preview,
                attachmentCount, chatMessage.publishDt(), chunk));
        }
        return chunks.size();
    }

    private void send(ChatNotificationEvent notification) {
        try {
            chatNotificationExecutor.execute(() -> {
                try {
                    kafkaProducerClient.publishChatNotification(notification);
                } catch (RuntimeException e) {
                    // 연결 / 타임아웃 / 4xx·5xx (FeignException)
                    warnDropped(notification, e);
                }
            });
        } catch (RejectedExecutionException e) {
            // 대기열이 가득 참 (kafka-producer 가 느리거나 죽음)
            warnDropped(notification, e);
        }
    }

    private static void warnDropped(ChatNotificationEvent notification, Throwable error) {
        log.warn("[chat-notification] publish fail, notification dropped messageId : {}, channelId : {}, receivers : {}, error : {}",
            notification.messageId(), notification.channelId(), notification.receiverUserIds().size(), error.getMessage());
    }
}
