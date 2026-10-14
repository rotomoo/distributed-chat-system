package com.distributed.chat.system.chatting.base.presence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import com.distributed.chat.system.common.util.NetworkUtil;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextImpl;
import org.springframework.session.Session;
import org.springframework.session.SessionRepository;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketHttpHeaders;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.client.standard.StandardWebSocketClient;
import org.springframework.web.socket.handler.TextWebSocketHandler;

/**
 * 진짜 채팅 서버(랜덤 포트) + 진짜 웹소켓 클라이언트 + 로컬 Redis. Eureka 대신 살아 있는 다른 채팅 서버 하나(10.255.255.9:45678)를
 * simple discovery 로 둔다. Lua (compare-and-set / compare-and-delete) 가 RedisTemplate 직렬화 값과 맞는지도 여기서 확인된다
 */
@DisplayName("[통합] 채팅 서버 접속 위치 Redis {userId} (실제 웹소켓) 테스트")
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = {
    "eureka.client.enabled=false",
    "spring.cloud.discovery.client.simple.instances.chatting[0].uri=http://10.255.255.9:45678"
})
class ChatPresenceIntegrationTest {

    private static final String OTHER_ALIVE = "ws://10.255.255.9:45678";
    private static final String DEAD = "ws://10.255.255.8:1";

    @LocalServerPort
    private int port;

    @Autowired
    @SuppressWarnings("rawtypes")
    private SessionRepository sessionRepository;

    @Autowired
    private RedisTemplate<String, Object> redisTemplate;

    private final List<WebSocketSession> clients = new ArrayList<>();
    private final List<String> sessionIds = new ArrayList<>();
    private final List<String> userIds = new ArrayList<>();

    @AfterEach
    @SuppressWarnings("unchecked")
    void tearDown() throws Exception {
        for (WebSocketSession client : clients) {
            if (client.isOpen()) {
                client.close();
            }
        }
        sessionIds.forEach(sessionRepository::deleteById);
        userIds.forEach(redisTemplate::delete);
    }

    @Test
    @DisplayName("success case : 처음 붙으면 {userId} 에 이 서버를 적고, 같은 서버의 두 번째 기기도 그대로 붙는다")
    void 처음_붙으면_이_서버를_적고_같은_서버의_두_번째_기기도_그대로_붙는다() throws Exception {
        // given
        String userId = newUserId();

        // when
        Client pc = connect(userId);
        Client phone = connect(userId);

        // then
        await().atMost(Duration.ofSeconds(5)).until(() -> pc.connected() && phone.connected());
        assertThat(redisTemplate.opsForValue().get(userId)).isEqualTo(me());
        assertThat(pc.session.isOpen()).isTrue();
        assertThat(phone.session.isOpen()).isTrue();
    }

    @Test
    @DisplayName("fail case : 살아 있는 다른 서버에 있는 유저는 4409 로 닫고 {userId} 는 그대로 둔다")
    void 살아_있는_다른_서버에_있는_유저는_4409_로_닫고_값은_그대로_둔다() throws Exception {
        // given
        String userId = newUserId();
        redisTemplate.opsForValue().set(userId, OTHER_ALIVE);

        // when
        Client client = connect(userId);

        // then
        await().atMost(Duration.ofSeconds(5)).until(() -> client.closeStatus.get() != null);
        assertThat(client.closeStatus.get().getCode()).isEqualTo(4409);
        assertThat(client.connected()).isFalse();
        assertThat(redisTemplate.opsForValue().get(userId)).isEqualTo(OTHER_ALIVE);
    }

    @Test
    @DisplayName("success case : 죽은 서버를 가리키면 이 서버로 바꾸고 붙는다")
    void 죽은_서버를_가리키면_이_서버로_바꾸고_붙는다() throws Exception {
        // given
        String userId = newUserId();
        redisTemplate.opsForValue().set(userId, DEAD);

        // when
        Client client = connect(userId);

        // then
        await().atMost(Duration.ofSeconds(5)).until(client::connected);
        assertThat(redisTemplate.opsForValue().get(userId)).isEqualTo(me());
    }

    @Test
    @DisplayName("success case : 이 서버의 마지막 기기가 끊기면 {userId} 를 지우고, 다른 기기가 남아 있으면 지우지 않는다")
    void 마지막_기기가_끊기면_지우고_다른_기기가_남아_있으면_지우지_않는다() throws Exception {
        // given
        String userId = newUserId();
        Client pc = connect(userId);
        Client phone = connect(userId);
        await().atMost(Duration.ofSeconds(5)).until(() -> pc.connected() && phone.connected());

        // when
        pc.session.close();
        TimeUnit.MILLISECONDS.sleep(300);
        Object afterFirst = redisTemplate.opsForValue().get(userId);
        phone.session.close();

        // then
        assertThat(afterFirst).isEqualTo(me());
        await().atMost(Duration.ofSeconds(5)).until(() -> redisTemplate.opsForValue().get(userId) == null);
    }

    @Test
    @DisplayName("success case : 마지막 기기가 끊길 때 {userId} 가 이미 다른 서버 값이면 지우지 않는다 (compare-and-delete)")
    void 마지막_기기가_끊길_때_다른_서버_값이면_지우지_않는다() throws Exception {
        // given
        String userId = newUserId();
        Client pc = connect(userId);
        await().atMost(Duration.ofSeconds(5)).until(pc::connected);
        redisTemplate.opsForValue().set(userId, OTHER_ALIVE);

        // when
        pc.session.close();
        TimeUnit.MILLISECONDS.sleep(500);

        // then
        assertThat(redisTemplate.opsForValue().get(userId)).isEqualTo(OTHER_ALIVE);
    }

    private String me() {
        return "ws://" + NetworkUtil.getServerIp() + ":" + port;
    }

    private String newUserId() {
        String userId = String.valueOf(ThreadLocalRandom.current().nextLong(9_000_000_000L, 9_900_000_000L));
        userIds.add(userId);
        return userId;
    }

    @SuppressWarnings("unchecked")
    private Client connect(String userId) throws Exception {
        Session session = sessionRepository.createSession();
        session.setAttribute("userId", userId);
        session.setAttribute("SPRING_SECURITY_CONTEXT", new SecurityContextImpl(
            UsernamePasswordAuthenticationToken.authenticated(userId, null, List.of(new SimpleGrantedAuthority("ROLE_USER")))));
        sessionRepository.save(session);
        sessionIds.add(session.getId());

        WebSocketHttpHeaders headers = new WebSocketHttpHeaders();
        headers.add("Cookie", "SESSION=" + Base64.getEncoder().encodeToString(session.getId().getBytes(StandardCharsets.UTF_8)));
        Client client = new Client();
        client.session = new StandardWebSocketClient()
            .execute(client, headers, URI.create("ws://localhost:" + port + "/websocket"))
            .get(5, TimeUnit.SECONDS);
        clients.add(client.session);
        return client;
    }

    private static final class Client extends TextWebSocketHandler {

        private WebSocketSession session;
        private final List<String> frames = new CopyOnWriteArrayList<>();
        private final AtomicReference<CloseStatus> closeStatus = new AtomicReference<>();

        boolean connected() {
            return frames.stream().anyMatch(frame -> frame.contains("\"type\":\"connected\""));
        }

        @Override
        protected void handleTextMessage(WebSocketSession session, TextMessage message) {
            frames.add(message.getPayload());
        }

        @Override
        public void afterConnectionClosed(WebSocketSession session, CloseStatus status) {
            closeStatus.set(status);
        }
    }
}
