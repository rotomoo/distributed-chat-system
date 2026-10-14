package com.distributed.chat.system.notification.base.kafka;

import com.google.protobuf.InvalidProtocolBufferException;
import java.nio.charset.StandardCharsets;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.common.header.Header;
import org.springframework.kafka.support.KafkaHeaders;
import org.springframework.stereotype.Component;
import protobuf.EdaChatMessage.ChatNotification;

/**
 * 재시도를 다 써도 실패한 알림 (chat-notification-dlt). 알림은 유실을 허용하므로 다시 발행하지 않고 WARN 로그만 남긴다
 * (메시지는 이미 저장됐고 앱을 열면 받는다)
 */
@Component(NotificationDeadLetterHandler.BEAN_NAME)
@Slf4j
public class NotificationDeadLetterHandler {

    public static final String BEAN_NAME = "notificationDeadLetterHandler";

    public void handle(ConsumerRecord<String, byte[]> record) {
        String reason = header(record, KafkaHeaders.EXCEPTION_MESSAGE);
        try {
            ChatNotification notification = ChatNotification.parseFrom(record.value());
            log.warn("[dlt] notification dropped messageId : {}, channelId : {}, receivers : {}, partition : {}, offset : {}, reason : {}",
                notification.getMessageId(), notification.getChannelId(), notification.getReceiverUserIdsCount(),
                record.partition(), record.offset(), reason);
        } catch (InvalidProtocolBufferException e) {
            log.warn("[dlt] notification dropped (unreadable payload) partition : {}, offset : {}, reason : {}",
                record.partition(), record.offset(), reason);
        }
    }

    private static String header(ConsumerRecord<String, byte[]> record, String name) {
        Header header = record.headers().lastHeader(name);
        return header == null ? "" : new String(header.value(), StandardCharsets.UTF_8);
    }
}
