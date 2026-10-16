package com.distributed.chat.system.common.redis;

import static org.assertj.core.api.Assertions.assertThat;

import com.distributed.chat.system.common.redis.ChatPresenceKeys.ChatPresence;
import java.time.Duration;
import java.util.Map;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("[단위] ChatPresenceKeys 테스트")
class ChatPresenceKeysUnitTest {

    @Test
    @DisplayName("success case : 키는 userId 그대로, 값은 {server, status} JSON 텍스트이고 TTL 은 60초다")
    void 키는_userId_그대로_값은_server_status_JSON_텍스트이고_TTL_은_60초다() {
        // act & assert
        assertThat(ChatPresenceKeys.userKey(5L)).isEqualTo("5");
        assertThat(ChatPresenceKeys.userKeyValue("10.0.0.1:1111", "online"))
            .isEqualTo("{\"server\":\"ws://10.0.0.1:1111\",\"status\":\"online\"}");
        assertThat(ChatPresenceKeys.TTL).isEqualTo(Duration.ofSeconds(60));
        assertThat(ChatPresenceKeys.serverUri("10.0.0.1:1111")).isEqualTo("ws://10.0.0.1:1111");
        assertThat(ChatPresenceKeys.chatServerOf("ws://10.0.0.1:1111")).isEqualTo("10.0.0.1:1111");
        assertThat(ChatPresenceKeys.chatServerOf("10.0.0.1:1111")).isEqualTo("10.0.0.1:1111");
    }

    @Test
    @DisplayName("success case : 쓴 값을 문자열로 읽어도, JSON 직렬화로 읽은 Map 이어도 같은 {server, status} 다")
    void 쓴_값을_문자열로_읽어도_Map_으로_읽어도_같다() {
        // arrange
        String written = ChatPresenceKeys.userKeyValue("10.0.0.1:1111", "away");

        // act
        ChatPresence fromText = ChatPresenceKeys.parse(written);
        ChatPresence fromMap = ChatPresenceKeys.parse(Map.of("server", "ws://10.0.0.1:1111", "status", "away"));

        // assert
        assertThat(fromText).isEqualTo(new ChatPresence("ws://10.0.0.1:1111", "away"));
        assertThat(fromMap).isEqualTo(fromText);
        assertThat(fromText.chatServer()).isEqualTo("10.0.0.1:1111");
        assertThat(ChatPresenceKeys.statusOf(written)).isEqualTo("away");
    }

    @Test
    @DisplayName("success case : 예전 값(서버 주소만)은 문자열 그대로든 JSON 문자열이든 {server, online} 으로 읽는다")
    void 예전_값은_server_online_으로_읽는다() {
        // act & assert
        assertThat(ChatPresenceKeys.parse("ws://10.0.0.1:1111")).isEqualTo(new ChatPresence("ws://10.0.0.1:1111", "online"));
        assertThat(ChatPresenceKeys.parse("\"ws://10.0.0.1:1111\"")).isEqualTo(new ChatPresence("ws://10.0.0.1:1111", "online"));
        assertThat(ChatPresenceKeys.statusOf("ws://10.0.0.1:1111")).isEqualTo("online");
        assertThat(ChatPresenceKeys.parse("ws://10.0.0.1:1111").chatServer()).isEqualTo("10.0.0.1:1111");
    }

    @Test
    @DisplayName("success case : server 는 있는데 status 가 없거나 모르는 값이면 online 이다 (키가 있다 = 연결된 기기가 있다)")
    void status_가_없거나_모르는_값이면_online_이다() {
        // act & assert
        assertThat(ChatPresenceKeys.statusOf("{\"server\":\"ws://10.0.0.1:1111\"}")).isEqualTo("online");
        assertThat(ChatPresenceKeys.statusOf(Map.of("server", "ws://10.0.0.1:1111", "status", "busy"))).isEqualTo("online");
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "   ", "{", "{\"status\":\"online\"}", "{\"server\":\"\"}", "[1,2]", "123", "\"\"", "\"online\"", "10.0.0.1:1111"})
    @DisplayName("fail case : 알아볼 수 없는 값은 null (= offline) 이다")
    void 알아볼_수_없는_값은_offline_이다(String value) {
        // act & assert
        assertThat(ChatPresenceKeys.parse(value)).isNull();
        assertThat(ChatPresenceKeys.statusOf(value)).isEqualTo("offline");
    }

    @Test
    @DisplayName("fail case : 키가 없거나(null) 문자열 / Map 이 아니면 offline 이다")
    void 키가_없거나_문자열_Map_이_아니면_offline_이다() {
        // act & assert
        assertThat(ChatPresenceKeys.parse(null)).isNull();
        assertThat(ChatPresenceKeys.statusOf(null)).isEqualTo("offline");
        assertThat(ChatPresenceKeys.statusOf(1)).isEqualTo("offline");
    }
}
