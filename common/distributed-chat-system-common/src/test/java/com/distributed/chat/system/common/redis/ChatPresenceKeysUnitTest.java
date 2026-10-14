package com.distributed.chat.system.common.redis;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("[단위] ChatPresenceKeys 테스트")
class ChatPresenceKeysUnitTest {

    @Test
    @DisplayName("success case : 채팅 서버가 쓰는 키 모양 그대로 만든다")
    void 채팅_서버가_쓰는_키_모양_그대로_만든다() {
        // act & assert
        assertThat(ChatPresenceKeys.userKey(5L)).isEqualTo("5");
        assertThat(ChatPresenceKeys.userKeyValue("10.0.0.1:1111")).isEqualTo("ws://10.0.0.1:1111");
        assertThat(ChatPresenceKeys.chatServerOf("ws://10.0.0.1:1111")).isEqualTo("10.0.0.1:1111");
        assertThat(ChatPresenceKeys.chatServerOf("10.0.0.1:1111")).isEqualTo("10.0.0.1:1111");
    }

    @Test
    @DisplayName("success case : 접속 상태 키는 presence:{userId} 이고 TTL 은 60초다")
    void 접속_상태_키는_presence_userId_이고_TTL_은_60초다() {
        // act & assert
        assertThat(ChatPresenceKeys.presenceKey(5L)).isEqualTo("presence:5");
        assertThat(ChatPresenceKeys.presenceKey("5")).isEqualTo("presence:5");
        assertThat(ChatPresenceKeys.PRESENCE_TTL).isEqualTo(Duration.ofSeconds(60));
    }

    @Test
    @DisplayName("success case : online / away 는 그대로, 키가 없거나 모르는 값은 offline 으로 읽는다")
    void online_away_는_그대로_키가_없거나_모르는_값은_offline_으로_읽는다() {
        // act & assert
        assertThat(ChatPresenceKeys.statusOf("online")).isEqualTo("online");
        assertThat(ChatPresenceKeys.statusOf("away")).isEqualTo("away");
        assertThat(ChatPresenceKeys.statusOf(null)).isEqualTo("offline");
        assertThat(ChatPresenceKeys.statusOf("busy")).isEqualTo("offline");
        assertThat(ChatPresenceKeys.statusOf(1)).isEqualTo("offline");
    }
}
