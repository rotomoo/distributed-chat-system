package com.distributed.chat.system.notification.consumer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import com.distributed.chat.system.common.event.ChatNotificationEvent;
import com.distributed.chat.system.common.event.EventJson;
import com.distributed.chat.system.common.event.MalformedEventException;
import com.distributed.chat.system.notification.service.NotificationService;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@DisplayName("[단위] ChatNotificationConsumer 테스트")
@ExtendWith(MockitoExtension.class)
class ChatNotificationConsumerUnitTest {

    @Mock
    private NotificationService notificationService;

    @InjectMocks
    private ChatNotificationConsumer chatNotificationConsumer;

    @Test
    @DisplayName("success case : 알림 이벤트 JSON 을 읽어 알림 서비스로 넘긴다")
    void 알림_이벤트_JSON_을_읽어_알림_서비스로_넘긴다() {
        // arrange
        ChatNotificationEvent notification = new ChatNotificationEvent("m", 7L, 1L, "안녕", 0, 1L, List.of(2L));

        // act
        chatNotificationConsumer.consume(EventJson.write(notification));

        // assert
        verify(notificationService).notify(notification);
    }

    @Test
    @DisplayName("success case : 모르는 필드가 있어도 아는 필드로 읽어 넘긴다")
    void 모르는_필드가_있어도_아는_필드로_읽어_넘긴다() {
        // act
        chatNotificationConsumer.consume("{\"messageId\":\"m\",\"channelId\":7,\"senderId\":1,\"receiverUserIds\":[2],\"urgent\":true}");

        // assert
        verify(notificationService).notify(new ChatNotificationEvent("m", 7L, 1L, "", 0, 0L, List.of(2L)));
    }

    @Test
    @DisplayName("fail case : 읽을 수 없는 이벤트는 알림 서비스로 넘기지 않고 MalformedEventException 을 던진다 (재시도 없이 DLT 로)")
    void 읽을_수_없는_이벤트는_넘기지_않고_MalformedEventException_을_던진다() {
        // act
        Throwable thrown = catchThrowable(() -> chatNotificationConsumer.consume("\u0008\u0005"));

        // assert
        assertThat(thrown).isInstanceOf(MalformedEventException.class);
        verifyNoInteractions(notificationService);
    }
}
