package com.distributed.chat.system.notification.consumer;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import com.distributed.chat.system.notification.service.NotificationService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import protobuf.EdaChatMessage.ChatNotification;

@DisplayName("[단위] ChatNotificationConsumer 테스트")
@ExtendWith(MockitoExtension.class)
class ChatNotificationConsumerUnitTest {

    @Mock
    private NotificationService notificationService;

    @InjectMocks
    private ChatNotificationConsumer chatNotificationConsumer;

    @Test
    @DisplayName("success case : 알림 이벤트를 읽어 알림 서비스로 넘긴다")
    void 알림_이벤트를_읽어_알림_서비스로_넘긴다() {
        // arrange
        ChatNotification notification = ChatNotification.newBuilder().setMessageId("m").setChannelId(7L).setSenderId(1L)
            .addReceiverUserIds(2L).build();

        // act
        chatNotificationConsumer.consume(notification.toByteArray());

        // assert
        verify(notificationService).notify(notification);
    }

    @Test
    @DisplayName("fail case : 읽을 수 없는 이벤트는 버린다 (재시도하지 않음)")
    void 읽을_수_없는_이벤트는_버린다() {
        // act
        chatNotificationConsumer.consume(new byte[]{(byte) 0xFF, (byte) 0xFF, (byte) 0xFF});

        // assert
        verifyNoInteractions(notificationService);
    }
}
