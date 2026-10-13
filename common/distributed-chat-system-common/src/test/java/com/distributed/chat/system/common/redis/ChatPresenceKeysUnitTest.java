package com.distributed.chat.system.common.redis;

import static org.assertj.core.api.Assertions.assertThat;

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
        assertThat(ChatPresenceKeys.chatServerKey("10.0.0.1:1111")).isEqualTo("chatServerUri:10.0.0.1:1111");
        assertThat(ChatPresenceKeys.chatServerOf("ws://10.0.0.1:1111")).isEqualTo("10.0.0.1:1111");
        assertThat(ChatPresenceKeys.chatServerOf("10.0.0.1:1111")).isEqualTo("10.0.0.1:1111");
    }
}
