package com.distributed.chat.system.chatting.base.presence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import com.distributed.chat.system.common.redis.ChatPresenceKeys;
import com.distributed.chat.system.common.util.NetworkUtil;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
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
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.StringRedisTemplate;
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
 * simple discovery 로 둔다. Lua (cjson 으로 server 비교) 가 실제 저장 값과 맞는지도 여기서 확인된다.
 * <ul>
 *   <li>값은 문자열로 읽어(StringRedisTemplate) 저장된 JSON 텍스트 그대로 비교한다</li>
 *   <li>예전 값은 예전 채팅 서버처럼 JSON 직렬화 RedisTemplate 로 서버 주소 문자열을 넣는다 ("\"ws://...\"" 로 저장된다)</li>
 *   <li>TTL 갱신 주기(20초)는 바꿔 끼운 시계를 앞으로 돌려서 본다</li>
 * </ul>
 */
@DisplayName("[통합] 채팅 서버 접속 정보 Redis {userId} = {server, status} (실제 웹소켓) 테스트")
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

    @Autowired
    private StringRedisTemplate stringRedisTemplate;

    @Autowired
    private MutableClock clock;

    @Autowired
    private UserLocationRegistry userLocationRegistry;

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

    // ── 접속 위치 (server) ─────────────────────────────────────

    @Test
    @DisplayName("success case : 처음 붙으면 {userId} 에 {이 서버, online} 을 TTL 60초로 적고, 같은 서버의 두 번째 기기도 그대로 붙는다")
    void 처음_붙으면_이_서버_online_을_TTL_60초로_적고_두_번째_기기도_그대로_붙는다() throws Exception {
        // given
        String userId = newUserId();

        // when
        Client pc = connect(userId);
        Client phone = connect(userId);

        // then
        await().atMost(Duration.ofSeconds(5)).until(() -> pc.connected() && phone.connected());
        assertThat(value(userId)).isEqualTo(mine("online"));
        assertThat(ttl(userId)).isBetween(1L, 60L);
        assertThat(pc.session.isOpen()).isTrue();
        assertThat(phone.session.isOpen()).isTrue();
    }

    @Test
    @DisplayName("success case : 읽는 쪽(JSON 직렬화 RedisTemplate)으로 읽어도 {server, status} 로 풀린다")
    void 읽는_쪽_RedisTemplate_으로_읽어도_server_status_로_풀린다() throws Exception {
        // given
        String userId = newUserId();
        Client pc = connect(userId);
        await().atMost(Duration.ofSeconds(5)).until(pc::connected);

        // when
        Object read = redisTemplate.opsForValue().get(userId);

        // then
        assertThat(ChatPresenceKeys.parse(read)).isEqualTo(new ChatPresenceKeys.ChatPresence(me(), "online"));
    }

    @Test
    @DisplayName("fail case : 살아 있는 다른 서버에 있는 유저는 4409 로 닫고 {userId} 는 그대로 둔다")
    void 살아_있는_다른_서버에_있는_유저는_4409_로_닫고_값은_그대로_둔다() throws Exception {
        // given
        String userId = newUserId();
        String other = other("away");
        stringRedisTemplate.opsForValue().set(userId, other, Duration.ofSeconds(60));

        // when
        Client client = connect(userId);

        // then
        await().atMost(Duration.ofSeconds(5)).until(() -> client.closeStatus.get() != null);
        assertThat(client.closeStatus.get().getCode()).isEqualTo(4409);
        assertThat(client.connected()).isFalse();
        assertThat(value(userId)).isEqualTo(other);
    }

    @Test
    @DisplayName("fail case : 예전 값(서버 주소만)이 살아 있는 다른 서버여도 4409 로 닫고 그대로 둔다")
    void 예전_값이_살아_있는_다른_서버여도_4409_로_닫고_그대로_둔다() throws Exception {
        // given : 예전 채팅 서버가 쓰던 그대로
        String userId = newUserId();
        redisTemplate.opsForValue().set(userId, OTHER_ALIVE);
        String legacy = value(userId);

        // when
        Client client = connect(userId);

        // then
        await().atMost(Duration.ofSeconds(5)).until(() -> client.closeStatus.get() != null);
        assertThat(client.closeStatus.get().getCode()).isEqualTo(4409);
        assertThat(value(userId)).isEqualTo(legacy).isEqualTo("\"" + OTHER_ALIVE + "\"");
    }

    @Test
    @DisplayName("success case : 죽은 서버를 가리키면 (새 값이든 예전 값이든) 이 서버로 바꾸고 TTL 60초를 건다")
    void 죽은_서버를_가리키면_이_서버로_바꾸고_TTL_60초를_건다() throws Exception {
        // given
        String newFormat = newUserId();
        String legacyFormat = newUserId();
        stringRedisTemplate.opsForValue().set(newFormat, ChatPresenceKeys.userKeyValue("10.255.255.8:1", "away"));
        redisTemplate.opsForValue().set(legacyFormat, DEAD);

        // when
        Client first = connect(newFormat);
        Client second = connect(legacyFormat);

        // then
        await().atMost(Duration.ofSeconds(5)).until(() -> first.connected() && second.connected());
        assertThat(value(newFormat)).isEqualTo(mine("online"));
        assertThat(value(legacyFormat)).isEqualTo(mine("online"));
        assertThat(ttl(newFormat)).isBetween(1L, 60L);
        assertThat(ttl(legacyFormat)).isBetween(1L, 60L);
    }

    @Test
    @DisplayName("success case : 예전 값이 이 서버를 가리키면 그대로 붙고 새 값 + TTL 로 바꿔 쓴다")
    void 예전_값이_이_서버를_가리키면_그대로_붙고_새_값_TTL_로_바꿔_쓴다() throws Exception {
        // given : TTL 없는 예전 값
        String userId = newUserId();
        redisTemplate.opsForValue().set(userId, me());

        // when
        Client pc = connect(userId);

        // then
        await().atMost(Duration.ofSeconds(5)).until(pc::connected);
        assertThat(value(userId)).isEqualTo(mine("online"));
        assertThat(ttl(userId)).isBetween(1L, 60L);
    }

    @Test
    @DisplayName("success case : 이 서버의 마지막 기기가 끊길 때만 {userId} 를 지운다")
    void 마지막_기기가_끊길_때만_지운다() throws Exception {
        // given
        String userId = newUserId();
        Client pc = connect(userId);
        Client phone = connect(userId);
        await().atMost(Duration.ofSeconds(5)).until(() -> pc.connected() && phone.connected());

        // when
        pc.session.close();
        TimeUnit.MILLISECONDS.sleep(300);
        String afterFirst = value(userId);
        phone.session.close();

        // then
        assertThat(afterFirst).isEqualTo(mine("online"));
        await().atMost(Duration.ofSeconds(5)).until(() -> value(userId) == null);
    }

    @Test
    @DisplayName("success case : 다른 서버가 가져간 뒤에는 상태를 쓰지도, 마지막 기기가 끊겨도 지우지도 않는다")
    void 다른_서버가_가져간_뒤에는_쓰지도_지우지도_않는다() throws Exception {
        // given : 다른 서버가 이 유저를 가져가서 자기 상태(away)를 썼다
        String userId = newUserId();
        Client pc = connect(userId);
        await().atMost(Duration.ofSeconds(5)).until(pc::connected);
        String other = other("away");
        stringRedisTemplate.opsForValue().set(userId, other, Duration.ofSeconds(60));

        // when
        pc.send("{\"type\":\"presence\",\"state\":\"away\"}");
        pc.send("{\"type\":\"presence\",\"state\":\"active\"}");
        clock.advance(Duration.ofSeconds(21));
        pc.send("{\"type\":\"pong\"}");
        TimeUnit.MILLISECONDS.sleep(300);
        String afterFrames = value(userId);
        pc.session.close();
        TimeUnit.MILLISECONDS.sleep(500);

        // then
        assertThat(afterFrames).isEqualTo(other);
        assertThat(value(userId)).isEqualTo(other);
    }

    @Test
    @DisplayName("success case : 예전 값이 다른 서버를 가리키면 마지막 기기가 끊겨도 지우지 않는다")
    void 예전_값이_다른_서버를_가리키면_지우지_않는다() throws Exception {
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

    @Test
    @DisplayName("success case : 접속 위치를 잡는 것만으로(상태 쓰기 전) 새로 적은 값에는 TTL 60초가 걸려 있다 (처음 / 죽은 서버 넘겨받기)")
    void 접속_위치를_잡는_것만으로_새로_적은_값에는_TTL_60초가_걸려_있다() {
        // given : 웹소켓 없이 Redis 스크립트만 본다 (핸들러가 바로 뒤에 상태를 다시 쓰므로 그 전 상태를 따로 확인)
        String first = newUserId();
        String takeover = newUserId();
        redisTemplate.opsForValue().set(takeover, DEAD);
        String chatServer = NetworkUtil.getServerIp() + ":" + port;

        // when
        boolean claimedFirst = userLocationRegistry.claim(first, chatServer);
        boolean claimedTakeover = userLocationRegistry.claim(takeover, chatServer);

        // then
        assertThat(claimedFirst).isTrue();
        assertThat(claimedTakeover).isTrue();
        assertThat(value(first)).isEqualTo(mine("online"));
        assertThat(value(takeover)).isEqualTo(mine("online"));
        assertThat(ttl(first)).isBetween(1L, 60L);
        assertThat(ttl(takeover)).isBetween(1L, 60L);
    }

    // ── 접속 상태 (status) ─────────────────────────────────────

    @Test
    @DisplayName("success case : away 프레임을 보내면 status 가 away, active 를 보내면 online 이 되고 매번 TTL 60초가 걸린다")
    void away_프레임을_보내면_away_active_를_보내면_online_이_된다() throws Exception {
        // given
        String userId = newUserId();
        Client pc = connect(userId);
        await().atMost(Duration.ofSeconds(5)).until(pc::connected);

        // when
        pc.send("{\"type\":\"presence\",\"state\":\"away\"}");

        // then
        await().atMost(Duration.ofSeconds(5)).until(() -> mine("away").equals(value(userId)));
        assertThat(ttl(userId)).isBetween(1L, 60L);

        // when
        pc.send("{\"type\":\"presence\",\"state\":\"active\"}");

        // then
        await().atMost(Duration.ofSeconds(5)).until(() -> mine("online").equals(value(userId)));
        assertThat(ttl(userId)).isBetween(1L, 60L);
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
        assertThat(value(userId)).isEqualTo(mine("online"));

        // when
        phone.send("{\"type\":\"presence\",\"state\":\"away\"}");

        // then
        await().atMost(Duration.ofSeconds(5)).until(() -> mine("away").equals(value(userId)));
    }

    @Test
    @DisplayName("success case : active 기기가 끊기고 away 기기만 남으면 away 로 바뀌고 키는 남는다")
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
        await().atMost(Duration.ofSeconds(5)).until(() -> mine("away").equals(value(userId)));
    }

    @Test
    @DisplayName("success case : 20초가 지난 뒤 받은 프레임(pong)이 TTL 을 다시 60초로 늘리고, 20초 안에는 늘리지 않는다")
    void _20초가_지난_뒤_받은_프레임이_TTL_을_다시_60초로_늘린다() throws Exception {
        // given : TTL 이 거의 다 된 상태를 만든다
        String userId = newUserId();
        Client pc = connect(userId);
        await().atMost(Duration.ofSeconds(5)).until(pc::connected);
        stringRedisTemplate.expire(userId, Duration.ofSeconds(5));

        // when : 아직 20초 안
        clock.advance(Duration.ofSeconds(10));
        pc.send("{\"type\":\"pong\"}");
        TimeUnit.MILLISECONDS.sleep(300);

        // then
        assertThat(ttl(userId)).isLessThanOrEqualTo(5L);

        // when : 20초 지남
        clock.advance(Duration.ofSeconds(11));
        pc.send("{\"type\":\"pong\"}");

        // then
        await().atMost(Duration.ofSeconds(5)).until(() -> ttl(userId) > 50L);
        assertThat(value(userId)).isEqualTo(mine("online"));
    }

    @Test
    @DisplayName("success case : 기기가 붙어 있는데 키가 사라졌으면(만료 / failover) 다음 갱신 때 이 서버가 다시 적는다")
    void 기기가_붙어_있는데_키가_사라졌으면_다음_갱신_때_다시_적는다() throws Exception {
        // given
        String userId = newUserId();
        Client pc = connect(userId);
        await().atMost(Duration.ofSeconds(5)).until(pc::connected);
        stringRedisTemplate.delete(userId);

        // when
        clock.advance(Duration.ofSeconds(21));
        pc.send("{\"type\":\"pong\"}");

        // then
        await().atMost(Duration.ofSeconds(5)).until(() -> mine("online").equals(value(userId)));
        assertThat(ttl(userId)).isBetween(1L, 60L);
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
        assertThat(value(userId)).isEqualTo(mine("online"));
    }

    private String value(String userId) {
        return stringRedisTemplate.opsForValue().get(userId);
    }

    private long ttl(String userId) {
        Long ttl = stringRedisTemplate.getExpire(userId, TimeUnit.SECONDS);
        return ttl == null ? -2L : ttl;
    }

    private String mine(String status) {
        return ChatPresenceKeys.userKeyValue(NetworkUtil.getServerIp() + ":" + port, status);
    }

    private static String other(String status) {
        return ChatPresenceKeys.userKeyValue(ChatPresenceKeys.chatServerOf(OTHER_ALIVE), status);
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

    /**
     * 테스트에서 시간을 앞으로 돌리는 시계 (핸들러의 TTL 갱신 주기 판단에 쓰인다)
     */
    @TestConfiguration
    static class ClockConfig {

        @Bean
        @Primary
        MutableClock mutableClock() {
            return new MutableClock(Instant.now());
        }
    }

    static final class MutableClock extends Clock {

        private volatile Instant now;

        private MutableClock(Instant now) {
            this.now = now;
        }

        void advance(Duration duration) {
            now = now.plus(duration);
        }

        @Override
        public ZoneId getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return now;
        }
    }
}
