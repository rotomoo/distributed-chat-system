package com.distributed.chat.system.chatting.base.presence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Duration;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.cloud.client.DefaultServiceInstance;
import org.springframework.cloud.client.discovery.DiscoveryClient;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

/**
 * Lua 가 실제 Redis 에서 맞게 도는지는 ChatPresenceIntegrationTest 가 본다. 여기서는 어떤 값으로 무엇을 부르는지만 본다
 */
@DisplayName("[단위] UserLocationRegistry 테스트")
@ExtendWith(MockitoExtension.class)
class UserLocationRegistryUnitTest {

    private static final String ME = "10.0.0.1:1111";
    private static final String OTHER = "10.0.0.2:2222";
    private static final String DEAD = "10.0.0.9:9999";

    private static final String ME_ONLINE = "{\"server\":\"ws://" + ME + "\",\"status\":\"online\"}";
    private static final Duration TTL = Duration.ofSeconds(60);

    @Mock
    private StringRedisTemplate stringRedisTemplate;

    @Mock
    private ValueOperations<String, String> valueOperations;

    @Mock
    private DiscoveryClient discoveryClient;

    private UserLocationRegistry registry;

    @BeforeEach
    void setup() {
        lenient().when(stringRedisTemplate.opsForValue()).thenReturn(valueOperations);
        lenient().when(discoveryClient.getInstances("chatting")).thenReturn(List.of(
            new DefaultServiceInstance("me", "chatting", "10.0.0.1", 1111, false),
            new DefaultServiceInstance("other", "chatting", "10.0.0.2", 2222, false)));
        registry = new UserLocationRegistry(stringRedisTemplate, discoveryClient);
    }

    @Test
    @DisplayName("success case : 처음 붙는 유저는 SET NX EX 60 으로 {이 서버, online} 을 적는다")
    void 처음_붙는_유저는_SET_NX_EX_60_으로_이_서버_online_을_적는다() {
        // arrange
        when(valueOperations.setIfAbsent("5", ME_ONLINE, TTL)).thenReturn(true);

        // act & assert
        assertThat(registry.claim("5", ME)).isTrue();
        verify(valueOperations, never()).get(any());
    }

    @Test
    @DisplayName("success case : 이미 이 서버면 그대로 받는다 (같은 서버의 두 번째 기기, away 여도)")
    void 이미_이_서버면_그대로_받는다() {
        // arrange
        when(valueOperations.setIfAbsent("5", ME_ONLINE, TTL)).thenReturn(false);
        when(valueOperations.get("5")).thenReturn("{\"server\":\"ws://" + ME + "\",\"status\":\"away\"}");

        // act & assert
        assertThat(registry.claim("5", ME)).isTrue();
        verify(stringRedisTemplate, never()).execute(any(), anyList(), any(), any());
    }

    @Test
    @DisplayName("success case : 예전 값(서버 주소 JSON 문자열)이 이 서버여도 그대로 받는다")
    void 예전_값이_이_서버여도_그대로_받는다() {
        // arrange
        when(valueOperations.setIfAbsent("5", ME_ONLINE, TTL)).thenReturn(false);
        when(valueOperations.get("5")).thenReturn("\"ws://" + ME + "\"");

        // act & assert
        assertThat(registry.claim("5", ME)).isTrue();
    }

    @Test
    @DisplayName("fail case : 살아 있는 다른 서버에 있으면 (예전 값이어도) 거절하고 값을 바꾸지 않는다")
    void 살아_있는_다른_서버에_있으면_거절하고_값을_바꾸지_않는다() {
        // arrange
        when(valueOperations.setIfAbsent("5", ME_ONLINE, TTL)).thenReturn(false);
        when(valueOperations.get("5")).thenReturn("{\"server\":\"ws://" + OTHER + "\",\"status\":\"online\"}", "\"ws://" + OTHER + "\"");

        // act & assert
        assertThat(registry.claim("5", ME)).isFalse();
        assertThat(registry.claim("5", ME)).isFalse();
        verify(stringRedisTemplate, never()).execute(any(), anyList(), any(), any());
        verify(valueOperations, never()).set(any(), any());
    }

    @Test
    @DisplayName("success case : 죽은 서버(Eureka 에 없음)를 가리키면 그 값 그대로일 때만 이 서버로 바꾼다 (compare-and-set)")
    void 죽은_서버를_가리키면_그_값일_때만_이_서버로_바꾼다() {
        // arrange
        String dead = "{\"server\":\"ws://" + DEAD + "\",\"status\":\"away\"}";
        when(valueOperations.setIfAbsent("5", ME_ONLINE, TTL)).thenReturn(false);
        when(valueOperations.get("5")).thenReturn(dead);
        when(stringRedisTemplate.execute(UserLocationRegistry.COMPARE_AND_SET, List.of("5"), dead, ME_ONLINE)).thenReturn(1L);

        // act & assert
        assertThat(registry.claim("5", ME)).isTrue();
    }

    @Test
    @DisplayName("fail case : 죽은 서버 값을 바꾸려는 사이 다른 서버가 먼저 바꿨으면 거절한다")
    void 죽은_서버_값을_바꾸려는_사이_다른_서버가_먼저_바꿨으면_거절한다() {
        // arrange
        String dead = "\"ws://" + DEAD + "\"";
        when(valueOperations.setIfAbsent("5", ME_ONLINE, TTL)).thenReturn(false);
        when(valueOperations.get("5")).thenReturn(dead);
        when(stringRedisTemplate.execute(UserLocationRegistry.COMPARE_AND_SET, List.of("5"), dead, ME_ONLINE)).thenReturn(0L);

        // act & assert
        assertThat(registry.claim("5", ME)).isFalse();
    }

    @Test
    @DisplayName("success case : NX 와 GET 사이에 지워졌으면 다시 NX 로 잡는다")
    void NX_와_GET_사이에_지워졌으면_다시_NX_로_잡는다() {
        // arrange
        when(valueOperations.setIfAbsent("5", ME_ONLINE, TTL)).thenReturn(false, true);
        when(valueOperations.get("5")).thenReturn(null);

        // act & assert
        assertThat(registry.claim("5", ME)).isTrue();
    }

    @Test
    @DisplayName("success case : 상태는 server 가 이 서버일 때만 새 값을 EX 60 으로 쓰는 스크립트로 쓴다")
    void 상태는_server_가_이_서버일_때만_새_값을_EX_60_으로_쓰는_스크립트로_쓴다() {
        // arrange
        String meAway = "{\"server\":\"ws://" + ME + "\",\"status\":\"away\"}";
        when(stringRedisTemplate.execute(UserLocationRegistry.SET_IF_OWNER, List.of("5"), "ws://" + ME, meAway)).thenReturn(1L);

        // act
        boolean written = registry.writePresence("5", ME, "away");

        // assert
        assertThat(written).isTrue();
        assertThat(UserLocationRegistry.SET_IF_OWNER.getScriptAsString()).contains("'EX', 60");
        verify(valueOperations, never()).set(any(), any());
    }

    @Test
    @DisplayName("fail case : server 가 다른 서버면 쓰지 않았다고 돌려준다")
    void server_가_다른_서버면_쓰지_않았다고_돌려준다() {
        // arrange
        when(stringRedisTemplate.execute(UserLocationRegistry.SET_IF_OWNER, List.of("5"), "ws://" + ME, ME_ONLINE)).thenReturn(0L);

        // act & assert
        assertThat(registry.writePresence("5", ME, "online")).isFalse();
    }

    @Test
    @DisplayName("success case : 지울 때는 server 가 이 서버일 때만 지우는 스크립트를 쓴다 (compare-and-delete)")
    void 지울_때는_server_가_이_서버일_때만_지우는_스크립트를_쓴다() {
        // act
        registry.release("5", ME);

        // assert
        verify(stringRedisTemplate).execute(UserLocationRegistry.COMPARE_AND_DELETE, List.of("5"), "ws://" + ME);
        verify(stringRedisTemplate, never()).delete(any(String.class));
    }
}
