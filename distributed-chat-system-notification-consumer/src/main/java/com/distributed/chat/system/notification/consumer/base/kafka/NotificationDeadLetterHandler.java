package com.distributed.chat.system.notification.consumer.base.kafka;

import com.distributed.chat.system.common.event.ChatNotificationEvent;
import com.distributed.chat.system.common.event.EventJson;
import com.distributed.chat.system.common.event.MalformedEventException;
import java.nio.charset.StandardCharsets;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.common.header.Header;
import org.springframework.kafka.support.KafkaHeaders;
import org.springframework.stereotype.Component;

/**
 * 재시도를 다 써도 실패한 알림 (chat-notification-dlt). 알림은 유실을 허용하므로 다시 발행하지 않고 WARN 로그만 남긴다
 * (메시지는 이미 저장됐고 앱을 열면 받는다). 읽을 수 없는 값은 재시도 없이 바로 여기로 오고 같은 방식으로 버린다
 */
@Component(NotificationDeadLetterHandler.BEAN_NAME)
@Slf4j
public class NotificationDeadLetterHandler {

    public static final String BEAN_NAME = "notificationDeadLetterHandler";

    public void handle(ConsumerRecord<String, String> record) {
        String reason = header(record, KafkaHeaders.EXCEPTION_MESSAGE);
        try {
            ChatNotificationEvent notification = EventJson.read(record.value(), ChatNotificationEvent.class);
            log.warn("[dlt] notification dropped messageId : {}, channelId : {}, receivers : {}, partition : {}, offset : {}, reason : {}",
                notification.messageId(), notification.channelId(), notification.receiverUserIds().size(),
                record.partition(), record.offset(), reason);
        } catch (MalformedEventException e) {
            log.warn("[dlt] notification dropped (unreadable payload) partition : {}, offset : {}, reason : {}",
                record.partition(), record.offset(), reason);
        }
    }

    private static String header(ConsumerRecord<String, String> record, String name) {
        Header header = record.headers().lastHeader(name);
        return header == null ? "" : new String(header.value(), StandardCharsets.UTF_8);
    }
}
