package com.distributed.chat.system.chatting.base.presence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
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

@DisplayName("[단위] UserLocationRegistry 테스트")
@ExtendWith(MockitoExtension.class)
class UserLocationRegistryUnitTest {

    private static final String ME = "10.0.0.1:1111";
    private static final String OTHER = "10.0.0.2:2222";
    private static final String DEAD = "10.0.0.9:9999";

    @Mock
    private RedisTemplate<String, Object> redisTemplate;

    @Mock
    private ValueOperations<String, Object> valueOperations;

    @Mock
    private DiscoveryClient discoveryClient;

    private UserLocationRegistry registry;

    @BeforeEach
    void setup() {
        lenient().when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        lenient().when(discoveryClient.getInstances("chatting")).thenReturn(List.of(
            new DefaultServiceInstance("me", "chatting", "10.0.0.1", 1111, false),
            new DefaultServiceInstance("other", "chatting", "10.0.0.2", 2222, false)));
        registry = new UserLocationRegistry(redisTemplate, discoveryClient);
    }

    @Test
    @DisplayName("success case : 처음 붙는 유저는 SET NX 로 이 서버를 적는다")
    void 처음_붙는_유저는_SET_NX_로_이_서버를_적는다() {
        // arrange
        when(valueOperations.setIfAbsent("5", "ws://" + ME)).thenReturn(true);

        // act & assert
        assertThat(registry.claim("5", ME)).isTrue();
        verify(valueOperations, never()).get(any());
    }

    @Test
    @DisplayName("success case : 이미 이 서버면 그대로 받는다 (같은 서버의 두 번째 기기)")
    void 이미_이_서버면_그대로_받는다() {
        // arrange
        when(valueOperations.setIfAbsent("5", "ws://" + ME)).thenReturn(false);
        when(valueOperations.get("5")).thenReturn("ws://" + ME);

        // act & assert
        assertThat(registry.claim("5", ME)).isTrue();
        verify(redisTemplate, never()).execute(any(), anyList(), any(), any());
    }

    @Test
    @DisplayName("fail case : 살아 있는 다른 서버에 있으면 거절하고 값을 바꾸지 않는다")
    void 살아_있는_다른_서버에_있으면_거절하고_값을_바꾸지_않는다() {
        // arrange
        when(valueOperations.setIfAbsent("5", "ws://" + ME)).thenReturn(false);
        when(valueOperations.get("5")).thenReturn("ws://" + OTHER);

        // act & assert
        assertThat(registry.claim("5", ME)).isFalse();
        verify(redisTemplate, never()).execute(any(), anyList(), any(), any());
        verify(valueOperations, never()).set(any(), any());
    }

    @Test
    @DisplayName("success case : 죽은 서버(Eureka 에 없음)를 가리키면 그 값일 때만 이 서버로 바꾼다 (compare-and-set)")
    void 죽은_서버를_가리키면_그_값일_때만_이_서버로_바꾼다() {
        // arrange
        when(valueOperations.setIfAbsent("5", "ws://" + ME)).thenReturn(false);
        when(valueOperations.get("5")).thenReturn("ws://" + DEAD);
        when(redisTemplate.execute(UserLocationRegistry.COMPARE_AND_SET, List.of("5"), "ws://" + DEAD, "ws://" + ME)).thenReturn(1L);

        // act & assert
        assertThat(registry.claim("5", ME)).isTrue();
    }

    @Test
    @DisplayName("fail case : 죽은 서버 값을 바꾸려는 사이 다른 서버가 먼저 바꿨으면 거절한다")
    void 죽은_서버_값을_바꾸려는_사이_다른_서버가_먼저_바꿨으면_거절한다() {
        // arrange
        when(valueOperations.setIfAbsent("5", "ws://" + ME)).thenReturn(false);
        when(valueOperations.get("5")).thenReturn("ws://" + DEAD);
        when(redisTemplate.execute(UserLocationRegistry.COMPARE_AND_SET, List.of("5"), "ws://" + DEAD, "ws://" + ME)).thenReturn(0L);

        // act & assert
        assertThat(registry.claim("5", ME)).isFalse();
    }

    @Test
    @DisplayName("success case : NX 와 GET 사이에 지워졌으면 다시 NX 로 잡는다")
    void NX_와_GET_사이에_지워졌으면_다시_NX_로_잡는다() {
        // arrange
        when(valueOperations.setIfAbsent("5", "ws://" + ME)).thenReturn(false, true);
        when(valueOperations.get("5")).thenReturn(null);

        // act & assert
        assertThat(registry.claim("5", ME)).isTrue();
    }

    @Test
    @DisplayName("success case : 지울 때는 값이 이 서버일 때만 지우는 스크립트를 쓴다 (compare-and-delete)")
    void 지울_때는_값이_이_서버일_때만_지우는_스크립트를_쓴다() {
        // act
        registry.release("5", ME);

        // assert
        verify(redisTemplate).execute(UserLocationRegistry.COMPARE_AND_DELETE, List.of("5"), "ws://" + ME);
        verify(redisTemplate, never()).delete(any(String.class));
    }
}
