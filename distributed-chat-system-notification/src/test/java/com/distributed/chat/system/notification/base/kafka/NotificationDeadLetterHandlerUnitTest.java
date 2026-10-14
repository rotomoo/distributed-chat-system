package com.distributed.chat.system.notification.base.kafka;

import static org.assertj.core.api.Assertions.assertThatCode;

import java.nio.charset.StandardCharsets;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.kafka.support.KafkaHeaders;
import protobuf.EdaChatMessage.ChatNotification;

@DisplayName("[단위] NotificationDeadLetterHandler 테스트")
class NotificationDeadLetterHandlerUnitTest {

    private final NotificationDeadLetterHandler handler = new NotificationDeadLetterHandler();

    @Test
    @DisplayName("success case : DLT 에 온 알림은 로그만 남기고 버린다 (예외를 던지지 않아 오프셋이 넘어간다)")
    void DLT_에_온_알림은_로그만_남기고_버린다() {
        // arrange
        ConsumerRecord<String, byte[]> record = new ConsumerRecord<>("chat-notification-dlt", 0, 3L, "7",
            ChatNotification.newBuilder().setMessageId("m").setChannelId(7L).addReceiverUserIds(2L).build().toByteArray());
        record.headers().add(KafkaHeaders.EXCEPTION_MESSAGE, "mysql down".getBytes(StandardCharsets.UTF_8));
        ConsumerRecord<String, byte[]> unreadable = new ConsumerRecord<>("chat-notification-dlt", 0, 4L, "7", new byte[]{(byte) 0xFF});

        // act & assert
        assertThatCode(() -> handler.handle(record)).doesNotThrowAnyException();
        assertThatCode(() -> handler.handle(unreadable)).doesNotThrowAnyException();
    }
}
