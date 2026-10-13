package com.distributed.chat.system.client.api.base.presence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.SetOperations;
import org.springframework.data.redis.core.ValueOperations;

@DisplayName("[단위] UserPresenceReader 테스트")
@ExtendWith(MockitoExtension.class)
class UserPresenceReaderUnitTest {

    @Mock
    private RedisTemplate<String, Object> redisTemplate;

    @Mock
    private ValueOperations<String, Object> valueOperations;

    @Mock
    private SetOperations<String, Object> setOperations;

    @InjectMocks
    private UserPresenceReader userPresenceReader;

    @Test
    @DisplayName("success case : 접속 서버 값이 있고 그 서버의 접속자 Set 에 있으면 온라인이다")
    void 접속_서버_값이_있고_그_서버의_접속자_Set_에_있으면_온라인이다() {
        // arrange
        givenChatServers(List.of("1", "2"), "ws://10.0.0.1:1111", "ws://10.0.0.1:1111");
        when(redisTemplate.opsForSet()).thenReturn(setOperations);
        when(setOperations.isMember("chatServerUri:10.0.0.1:1111", "1", "2")).thenReturn(Map.of("1", true, "2", false));

        // act
        Set<Long> online = userPresenceReader.findOnlineUserIds(List.of(1L, 2L));

        // assert
        assertThat(online).containsExactly(1L);
    }

    @Test
    @DisplayName("success case : 접속 서버 값이 없으면 Set 을 보지 않고 오프라인이다")
    void 접속_서버_값이_없으면_Set_을_보지_않고_오프라인이다() {
        // arrange
        givenChatServers(List.of("3"), (Object) null);

        // act
        Set<Long> online = userPresenceReader.findOnlineUserIds(List.of(3L));

        // assert
        assertThat(online).isEmpty();
        verify(redisTemplate, never()).opsForSet();
    }

    @Test
    @DisplayName("success case : 서버별로 묶어서 서버당 한 번만 확인한다")
    void 서버별로_묶어서_서버당_한_번만_확인한다() {
        // arrange
        givenChatServers(List.of("1", "2", "3"), "ws://10.0.0.1:1111", "ws://10.0.0.2:2222", "ws://10.0.0.1:1111");
        when(redisTemplate.opsForSet()).thenReturn(setOperations);
        when(setOperations.isMember("chatServerUri:10.0.0.1:1111", "1", "3")).thenReturn(Map.of("1", true, "3", true));
        when(setOperations.isMember("chatServerUri:10.0.0.2:2222", new Object[]{"2"})).thenReturn(Map.of("2", true));

        // act
        Set<Long> online = userPresenceReader.findOnlineUserIds(List.of(1L, 2L, 3L));

        // assert
        assertThat(online).containsExactlyInAnyOrder(1L, 2L, 3L);
    }

    @Test
    @DisplayName("success case : 조회할 유저가 없으면 Redis 를 부르지 않는다")
    void 조회할_유저가_없으면_Redis_를_부르지_않는다() {
        // act
        Set<Long> online = userPresenceReader.findOnlineUserIds(List.of());

        // assert
        assertThat(online).isEmpty();
        verifyNoInteractions(redisTemplate);
    }

    private void givenChatServers(List<String> keys, Object... chatServerUris) {
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.multiGet(keys)).thenReturn(Arrays.asList(chatServerUris));
    }
}
