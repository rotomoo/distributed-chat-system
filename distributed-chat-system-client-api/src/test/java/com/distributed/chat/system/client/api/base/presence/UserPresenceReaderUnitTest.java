package com.distributed.chat.system.client.api.base.presence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.util.Arrays;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.cloud.client.DefaultServiceInstance;
import org.springframework.cloud.client.discovery.DiscoveryClient;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

@DisplayName("[단위] UserPresenceReader 테스트")
@ExtendWith(MockitoExtension.class)
class UserPresenceReaderUnitTest {

    @Mock
    private RedisTemplate<String, Object> redisTemplate;

    @Mock
    private ValueOperations<String, Object> valueOperations;

    @Mock
    private DiscoveryClient discoveryClient;

    private UserPresenceReader userPresenceReader;

    @BeforeEach
    void setup() {
        lenient().when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        lenient().when(discoveryClient.getInstances("chatting")).thenReturn(List.of(
            new DefaultServiceInstance("a", "chatting", "10.0.0.1", 1111, false),
            new DefaultServiceInstance("b", "chatting", "10.0.0.2", 2222, false)));
        userPresenceReader = new UserPresenceReader(redisTemplate, discoveryClient);
    }

    @Test
    @DisplayName("success case : 접속 위치가 살아 있는 채팅 서버면 온라인이다 (서버가 달라도)")
    void 접속_위치가_살아_있는_채팅_서버면_온라인이다() {
        // arrange
        givenLocations(List.of("1", "2"), "ws://10.0.0.1:1111", "ws://10.0.0.2:2222");

        // act
        Set<Long> online = userPresenceReader.findOnlineUserIds(List.of(1L, 2L));

        // assert
        assertThat(online).containsExactlyInAnyOrder(1L, 2L);
        verify(valueOperations, times(1)).multiGet(List.of("1", "2"));
    }

    @Test
    @DisplayName("success case : 접속 위치가 없거나 죽은 서버(Eureka 에 없음)를 가리키면 오프라인이다")
    void 접속_위치가_없거나_죽은_서버를_가리키면_오프라인이다() {
        // arrange
        givenLocations(List.of("1", "2", "3"), "ws://10.0.0.1:1111", null, "ws://10.0.0.9:9999");

        // act
        Set<Long> online = userPresenceReader.findOnlineUserIds(List.of(1L, 2L, 3L));

        // assert
        assertThat(online).containsExactly(1L);
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

    private void givenLocations(List<String> keys, Object... locations) {
        when(valueOperations.multiGet(keys)).thenReturn(Arrays.asList(locations));
    }
}
