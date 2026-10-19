package com.distributed.chat.system.notification.consumer.base.kafka;

import static org.assertj.core.api.Assertions.assertThatCode;

import com.distributed.chat.system.common.event.ChatNotificationEvent;
import com.distributed.chat.system.common.event.EventJson;
import java.nio.charset.StandardCharsets;
import java.util.List;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.kafka.support.KafkaHeaders;

@DisplayName("[단위] NotificationDeadLetterHandler 테스트")
class NotificationDeadLetterHandlerUnitTest {

    private final NotificationDeadLetterHandler handler = new NotificationDeadLetterHandler();

    @Test
    @DisplayName("success case : DLT 에 온 알림은 읽을 수 있든 없든 로그만 남기고 버린다 (예외를 던지지 않아 오프셋이 넘어간다)")
    void DLT_에_온_알림은_로그만_남기고_버린다() {
        // arrange
        ConsumerRecord<String, String> record = new ConsumerRecord<>("chat-notification-dlt", 0, 3L, "7",
            EventJson.write(new ChatNotificationEvent("m", 7L, 1L, "", 0, 1L, List.of(2L))));
        record.headers().add(KafkaHeaders.EXCEPTION_MESSAGE, "mysql down".getBytes(StandardCharsets.UTF_8));
        ConsumerRecord<String, String> unreadable = new ConsumerRecord<>("chat-notification-dlt", 0, 4L, "7", "{\"messageId\":");
        ConsumerRecord<String, String> empty = new ConsumerRecord<>("chat-notification-dlt", 0, 5L, "7", null);

        // act & assert
        assertThatCode(() -> handler.handle(record)).doesNotThrowAnyException();
        assertThatCode(() -> handler.handle(unreadable)).doesNotThrowAnyException();
        assertThatCode(() -> handler.handle(empty)).doesNotThrowAnyException();
    }
}
