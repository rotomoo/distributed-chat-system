package com.distributed.chat.system.common.kafka;

import static org.assertj.core.api.Assertions.assertThat;

import com.distributed.chat.system.common.redis.NotificationKeys;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("[단위] ChatNotificationTopic 테스트")
class ChatNotificationTopicUnitTest {

    @Test
    @DisplayName("success case : 100자 이하는 앞뒤 공백만 빼고 그대로 둔다")
    void 백자_이하는_앞뒤_공백만_빼고_그대로_둔다() {
        // act & assert
        assertThat(ChatNotificationTopic.preview("  점심 뭐 먹을까요?  ")).isEqualTo("점심 뭐 먹을까요?");
        assertThat(ChatNotificationTopic.preview("a".repeat(100))).hasSize(100);
        assertThat(ChatNotificationTopic.preview(null)).isEmpty();
    }

    @Test
    @DisplayName("success case : 100자를 넘으면 앞 100자로 자르고 이모지(서로게이트 쌍)는 반으로 자르지 않는다")
    void 백자를_넘으면_앞_백자로_자르고_이모지는_반으로_자르지_않는다() {
        // arrange : 이모지 101개 = UTF-16 202 글자
        String emojis = "😀".repeat(101);

        // act
        String preview = ChatNotificationTopic.preview(emojis);

        // assert
        assertThat(preview.codePointCount(0, preview.length())).isEqualTo(100);
        assertThat(preview).isEqualTo("😀".repeat(100));
        assertThat(ChatNotificationTopic.preview("가".repeat(150))).isEqualTo("가".repeat(100));
    }

    @Test
    @DisplayName("success case : 알림 묶기 키는 notify:throttle:{userId}:{channelId} 이고 30초다")
    void 알림_묶기_키는_notify_throttle_userId_channelId_이고_30초다() {
        // act & assert
        assertThat(NotificationKeys.throttleKey(5L, 7L)).isEqualTo("notify:throttle:5:7");
        assertThat(NotificationKeys.THROTTLE_SECONDS).isEqualTo(30);
        assertThat(ChatNotificationTopic.TOPIC).isEqualTo("chat-notification");
        assertThat(ChatNotificationTopic.MAX_RECEIVERS).isEqualTo(500);
    }
}
