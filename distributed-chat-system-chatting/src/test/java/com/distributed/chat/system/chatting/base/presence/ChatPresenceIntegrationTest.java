package com.distributed.chat.system.chatting.base.presence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import com.distributed.chat.system.common.redis.ChatPresenceKeys;
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
 * simple discovery 로 둔다. Lua (compare-and-set / compare-and-delete / 접속 상태 쓰기) 가 RedisTemplate 직렬화 값과 맞는지도 여기서 확인된다
 */
@DisplayName("[통합] 채팅 서버 접속 위치 Redis {userId} / 접속 상태 presence:{userId} (실제 웹소켓) 테스트")
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
        userIds.forEach(userId -> redisTemplate.delete(ChatPresenceKeys.presenceKey(userId)));
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

    // ── 접속 상태 presence:{userId} ────────────────────────────

    @Test
    @DisplayName("success case : 붙으면 presence:{userId} 에 online 을 TTL 60초로 쓴다")
    void 붙으면_online_을_TTL_60초로_쓴다() throws Exception {
        // given
        String userId = newUserId();

        // when
        Client pc = connect(userId);

        // then
        await().atMost(Duration.ofSeconds(5)).until(pc::connected);
        assertThat(redisTemplate.opsForValue().get(presenceKey(userId))).isEqualTo("online");
        assertThat(redisTemplate.getExpire(presenceKey(userId), TimeUnit.SECONDS)).isBetween(1L, 60L);
    }

    @Test
    @DisplayName("success case : away 프레임을 보내면 away, active 를 보내면 다시 online 이 되고 TTL 은 계속 걸려 있다")
    void away_프레임을_보내면_away_active_를_보내면_다시_online_이_된다() throws Exception {
        // given
        String userId = newUserId();
        Client pc = connect(userId);
        await().atMost(Duration.ofSeconds(5)).until(pc::connected);

        // when
        pc.send("{\"type\":\"presence\",\"state\":\"away\"}");

        // then
        await().atMost(Duration.ofSeconds(5)).until(() -> "away".equals(redisTemplate.opsForValue().get(presenceKey(userId))));
        assertThat(redisTemplate.getExpire(presenceKey(userId), TimeUnit.SECONDS)).isBetween(1L, 60L);

        // when
        pc.send("{\"type\":\"presence\",\"state\":\"active\"}");

        // then
        await().atMost(Duration.ofSeconds(5)).until(() -> "online".equals(redisTemplate.opsForValue().get(presenceKey(userId))));
        assertThat(pc.errorCount()).isZero();
    }

    @Test
    @DisplayName("success case : 두 번째 기기가 active 면 첫 기기가 away 여도 online, 둘 다 away 면 away 다")
    void 두_번째_기기가_active_면_online_둘_다_away_면_away_다() throws Exception {
        // given
        String userId = newUserId();
        Client pc = connect(userId);
        Client phone = connect(userId);
        await().atMost(Duration.ofSeconds(5)).until(() -> pc.connected() && phone.connected());

        // when
        pc.send("{\"type\":\"presence\",\"state\":\"away\"}");
        TimeUnit.MILLISECONDS.sleep(300);

        // then
        assertThat(redisTemplate.opsForValue().get(presenceKey(userId))).isEqualTo("online");

        // when
        phone.send("{\"type\":\"presence\",\"state\":\"away\"}");

        // then
        await().atMost(Duration.ofSeconds(5)).until(() -> "away".equals(redisTemplate.opsForValue().get(presenceKey(userId))));
    }

    @Test
    @DisplayName("success case : 마지막 기기가 끊길 때만 위치 키와 함께 presence:{userId} 를 지운다")
    void 마지막_기기가_끊길_때만_위치_키와_함께_presence_를_지운다() throws Exception {
        // given
        String userId = newUserId();
        Client pc = connect(userId);
        Client phone = connect(userId);
        await().atMost(Duration.ofSeconds(5)).until(() -> pc.connected() && phone.connected());

        // when
        pc.session.close();
        TimeUnit.MILLISECONDS.sleep(300);
        Object afterFirst = redisTemplate.opsForValue().get(presenceKey(userId));
        phone.session.close();

        // then
        assertThat(afterFirst).isEqualTo("online");
        await().atMost(Duration.ofSeconds(5)).until(() -> redisTemplate.opsForValue().get(presenceKey(userId)) == null
            && redisTemplate.opsForValue().get(userId) == null);
    }

    @Test
    @DisplayName("success case : active 기기가 끊기고 away 기기만 남으면 away 로 바뀐다")
    void active_기기가_끊기고_away_기기만_남으면_away_로_바뀐다() throws Exception {
        // given
        String userId = newUserId();
        Client pc = connect(userId);
        Client phone = connect(userId);
        await().atMost(Duration.ofSeconds(5)).until(() -> pc.connected() && phone.connected());
        phone.send("{\"type\":\"presence\",\"state\":\"away\"}");
        TimeUnit.MILLISECONDS.sleep(300);

        // when
        pc.session.close();

        // then
        await().atMost(Duration.ofSeconds(5)).until(() -> "away".equals(redisTemplate.opsForValue().get(presenceKey(userId))));
        assertThat(redisTemplate.opsForValue().get(userId)).isEqualTo(me());
    }

    @Test
    @DisplayName("success case : 위치 키가 다른 서버로 넘어간 뒤에는 상태를 쓰지도 지우지도 않는다")
    void 위치_키가_다른_서버로_넘어간_뒤에는_상태를_쓰지도_지우지도_않는다() throws Exception {
        // given : 다른 서버가 이 유저를 가져가서 자기 상태(away)를 썼다
        String userId = newUserId();
        Client pc = connect(userId);
        await().atMost(Duration.ofSeconds(5)).until(pc::connected);
        redisTemplate.opsForValue().set(userId, OTHER_ALIVE);
        redisTemplate.opsForValue().set(presenceKey(userId), "away", ChatPresenceKeys.PRESENCE_TTL);

        // when
        pc.send("{\"type\":\"presence\",\"state\":\"away\"}");
        pc.send("{\"type\":\"presence\",\"state\":\"active\"}");
        TimeUnit.MILLISECONDS.sleep(300);
        Object afterActive = redisTemplate.opsForValue().get(presenceKey(userId));
        pc.session.close();
        TimeUnit.MILLISECONDS.sleep(500);

        // then
        assertThat(afterActive).isEqualTo("away");
        assertThat(redisTemplate.opsForValue().get(presenceKey(userId))).isEqualTo("away");
        assertThat(redisTemplate.opsForValue().get(userId)).isEqualTo(OTHER_ALIVE);
    }

    @Test
    @DisplayName("fail case : 모르는 state 는 4002 오류를 돌려주고 상태를 바꾸지 않는다")
    void 모르는_state_는_4002_오류를_돌려주고_상태를_바꾸지_않는다() throws Exception {
        // given
        String userId = newUserId();
        Client pc = connect(userId);
        await().atMost(Duration.ofSeconds(5)).until(pc::connected);

        // when
        pc.send("{\"type\":\"presence\",\"state\":\"busy\"}");

        // then
        await().atMost(Duration.ofSeconds(5)).until(() -> pc.errorCount() == 1);
        assertThat(pc.frames).anyMatch(frame -> frame.contains("\"code\":4002"));
        assertThat(redisTemplate.opsForValue().get(presenceKey(userId))).isEqualTo("online");
    }

    private static String presenceKey(String userId) {
        return ChatPresenceKeys.presenceKey(userId);
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

        long errorCount() {
            return frames.stream().filter(frame -> frame.contains("\"type\":\"error\"")).count();
        }

        void send(String payload) throws Exception {
            session.sendMessage(new TextMessage(payload));
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
