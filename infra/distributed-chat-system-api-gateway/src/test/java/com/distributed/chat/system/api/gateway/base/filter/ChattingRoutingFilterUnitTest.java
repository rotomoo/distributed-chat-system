package com.distributed.chat.system.api.gateway.base.filter;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

import java.net.URI;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.cloud.client.DefaultServiceInstance;
import org.springframework.cloud.client.discovery.DiscoveryClient;
import org.springframework.cloud.gateway.route.Route;
import org.springframework.cloud.gateway.support.ServerWebExchangeUtils;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.http.HttpCookie;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import reactor.core.publisher.Mono;

@DisplayName("[단위] ChattingRoutingFilter 테스트")
@ExtendWith(MockitoExtension.class)
class ChattingRoutingFilterUnitTest {

    private static final URI LOAD_BALANCED = URI.create("lb:ws://CHATTING");

    @Mock
    private RedisTemplate<String, Object> redisTemplate;

    @Mock
    private ValueOperations<String, Object> valueOperations;

    @Mock
    private DiscoveryClient discoveryClient;

    private ChattingRoutingFilter chattingRoutingFilter;

    @BeforeEach
    void setup() {
        lenient().when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        lenient().when(discoveryClient.getInstances("chatting")).thenReturn(List.of(
            new DefaultServiceInstance("a", "chatting", "10.0.0.1", 1111, false)));
        chattingRoutingFilter = new ChattingRoutingFilter(redisTemplate, discoveryClient);
    }

    @Test
    @DisplayName("success case : 이미 접속한 서버가 살아 있으면 (away 여도) 그 서버로 보낸다 (유저 하나 = 서버 하나)")
    void 이미_접속한_서버가_살아_있으면_그_서버로_보낸다() {
        // arrange : JSON 직렬화 RedisTemplate 이 {server, status} 를 읽으면 Map 이 온다
        when(valueOperations.get("5")).thenReturn(Map.of("server", "ws://10.0.0.1:1111", "status", "away"));

        // act
        URI routed = route("5");

        // assert
        assertThat(routed).isEqualTo(URI.create("ws://10.0.0.1:1111"));
    }

    @Test
    @DisplayName("success case : 접속한 서버가 죽었으면(Eureka 에 없음) 로드밸런서에 맡긴다")
    void 접속한_서버가_죽었으면_로드밸런서에_맡긴다() {
        // arrange
        when(valueOperations.get("5")).thenReturn(Map.of("server", "ws://10.0.0.9:9999", "status", "online"));

        // act
        URI routed = route("5");

        // assert
        assertThat(routed).isEqualTo(LOAD_BALANCED);
    }

    @Test
    @DisplayName("success case : 예전 값(서버 주소 문자열)도 그 서버가 살아 있으면 그 서버로 보낸다")
    void 예전_값도_그_서버가_살아_있으면_그_서버로_보낸다() {
        // arrange
        when(valueOperations.get("5")).thenReturn("ws://10.0.0.1:1111");

        // act
        URI routed = route("5");

        // assert
        assertThat(routed).isEqualTo(URI.create("ws://10.0.0.1:1111"));
    }

    @Test
    @DisplayName("success case : 알아볼 수 없는 값이면 로드밸런서에 맡긴다")
    void 알아볼_수_없는_값이면_로드밸런서에_맡긴다() {
        // arrange
        when(valueOperations.get("5")).thenReturn(Map.of("status", "online"));

        // act
        URI routed = route("5");

        // assert
        assertThat(routed).isEqualTo(LOAD_BALANCED);
    }

    @Test
    @DisplayName("success case : 접속 위치가 없으면(첫 접속) 로드밸런서에 맡긴다")
    void 접속_위치가_없으면_로드밸런서에_맡긴다() {
        // arrange
        when(valueOperations.get("5")).thenReturn(null);

        // act
        URI routed = route("5");

        // assert
        assertThat(routed).isEqualTo(LOAD_BALANCED);
    }

    private URI route(String userId) {
        MockServerWebExchange exchange = MockServerWebExchange.from(
            MockServerHttpRequest.get("/websocket").cookie(new HttpCookie("userId", userId)));
        exchange.getAttributes().put(ServerWebExchangeUtils.GATEWAY_ROUTE_ATTR,
            Route.async().id("chatting").uri(LOAD_BALANCED).predicate(serverWebExchange -> true).build());

        chattingRoutingFilter.apply(new Object()).filter(exchange, filtered -> Mono.empty()).block();

        Route route = exchange.getAttribute(ServerWebExchangeUtils.GATEWAY_ROUTE_ATTR);
        return route.getUri();
    }
}
