package com.distributed.chat.system.chatting.base.heartbeat;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import com.distributed.chat.system.common.redis.ChatPresenceKeys;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
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
import org.springframework.boot.test.autoconfigure.actuate.observability.AutoConfigureObservability;
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
 * 진짜 채팅 서버(랜덤 포트) + 진짜 웹소켓 클라이언트. 로그인은 client-api 처럼 Redis 세션(Spring Session)에 SecurityContext 와 userId 를 넣고
 * SESSION 쿠키로 붙는다. Redis 는 로컬 스택, Eureka 는 끈다. ping 200ms / timeout 600ms 로 줄인다
 */
@DisplayName("[통합] 채팅 서버 앱 수준 하트비트 (실제 웹소켓) 테스트")
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = {
    "eureka.client.enabled=false",
    "chat.heartbeat.ping-interval-ms=200",
    "chat.heartbeat.timeout-ms=600"
})
@AutoConfigureObservability
class ChatHeartbeatIntegrationTest {

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
        // 테스트 유저의 접속 위치 / 접속 상태 키만 지운다 (실제 유저와 겹치지 않는 큰 ID)
        userIds.forEach(userId -> redisTemplate.delete(List.of(ChatPresenceKeys.userKey(userId), ChatPresenceKeys.presenceKey(userId))));
    }

    @Test
    @DisplayName("success case : 접속하면 connected 다음에 ping 을 주기적으로 받는다")
    void 접속하면_connected_다음에_ping_을_주기적으로_받는다() throws Exception {
        // given
        RecordingClient recorder = new RecordingClient(false);

        // when
        connect(recorder);

        // then
        await().atMost(Duration.ofSeconds(5)).untilAsserted(() ->
            assertThat(recorder.frames).filteredOn(frame -> frame.contains("\"type\":\"ping\"")).hasSizeGreaterThanOrEqualTo(2));
        assertThat(recorder.frames.get(0)).contains("\"type\":\"connected\"");
    }

    @Test
    @DisplayName("fail case : ping 에 답하지 않는 클라이언트는 timeout 뒤 SESSION_NOT_RELIABLE(4500) 로 닫힌다")
    void ping_에_답하지_않는_클라이언트는_timeout_뒤_닫힌다() throws Exception {
        // given
        RecordingClient silent = new RecordingClient(false);

        // when
        connect(silent);

        // then
        await().atMost(Duration.ofSeconds(5)).untilAsserted(() -> assertThat(silent.closeStatus.get()).isNotNull());
        assertThat(silent.closeStatus.get().getCode()).isEqualTo(CloseStatus.SESSION_NOT_RELIABLE.getCode());
    }

    @Test
    @DisplayName("success case : ping 마다 pong 으로 답하는 클라이언트는 timeout 이 몇 번 지나도 열려 있다")
    void ping_마다_pong_으로_답하는_클라이언트는_timeout_이_몇_번_지나도_열려_있다() throws Exception {
        // given
        RecordingClient ponging = new RecordingClient(true);

        // when
        WebSocketSession session = connect(ponging);
        TimeUnit.MILLISECONDS.sleep(2_500); // timeout(600ms) 의 4배 넘게

        // then
        assertThat(session.isOpen()).isTrue();
        assertThat(ponging.closeStatus.get()).isNull();
        assertThat(ponging.frames).filteredOn(frame -> frame.contains("\"type\":\"ping\"")).hasSizeGreaterThanOrEqualTo(5);
    }

    @Test
    @DisplayName("success case : 로그인 없이 /actuator/prometheus 에서 기본 메트릭(JVM, 톰캣)을 볼 수 있다")
    void 로그인_없이_actuator_prometheus_에서_기본_메트릭을_볼_수_있다() throws Exception {
        // given : 소켓 하나가 ping 을 받는 중
        RecordingClient ponging = new RecordingClient(true);
        connect(ponging);
        await().atMost(Duration.ofSeconds(5)).until(() -> ponging.frames.stream().anyMatch(frame -> frame.contains("\"type\":\"ping\"")));

        // when
        HttpResponse<String> response = HttpClient.newHttpClient().send(
            HttpRequest.newBuilder(URI.create("http://localhost:" + port + "/actuator/prometheus")).build(),
            HttpResponse.BodyHandlers.ofString());

        // then
        assertThat(response.statusCode()).isEqualTo(200);
        assertThat(response.body()).contains("jvm_memory_used_bytes", "tomcat_sessions", "application=\"chatting\"");
    }

    @SuppressWarnings("unchecked")
    private WebSocketSession connect(RecordingClient handler) throws Exception {
        String userId = String.valueOf(ThreadLocalRandom.current().nextLong(9_000_000_000L, 9_900_000_000L));
        userIds.add(userId);

        // client-api 로그인과 같은 모양의 세션 : SecurityContext + userId
        Session session = sessionRepository.createSession();
        session.setAttribute("userId", userId);
        session.setAttribute("SPRING_SECURITY_CONTEXT", new SecurityContextImpl(
            UsernamePasswordAuthenticationToken.authenticated(userId, null, List.of(new SimpleGrantedAuthority("ROLE_USER")))));
        sessionRepository.save(session);
        sessionIds.add(session.getId());

        WebSocketHttpHeaders headers = new WebSocketHttpHeaders();
        headers.add("Cookie", "SESSION=" + Base64.getEncoder().encodeToString(session.getId().getBytes(StandardCharsets.UTF_8)));
        WebSocketSession client = new StandardWebSocketClient()
            .execute(handler, headers, URI.create("ws://localhost:" + port + "/websocket"))
            .get(5, TimeUnit.SECONDS);
        clients.add(client);
        return client;
    }

    /**
     * 받은 프레임을 모으고, 원하면 ping 에 pong 으로 답한다
     */
    private static final class RecordingClient extends TextWebSocketHandler {

        private final boolean replyPong;
        private final List<String> frames = new CopyOnWriteArrayList<>();
        private final AtomicReference<CloseStatus> closeStatus = new AtomicReference<>();

        private RecordingClient(boolean replyPong) {
            this.replyPong = replyPong;
        }

        @Override
        protected void handleTextMessage(WebSocketSession session, TextMessage message) throws Exception {
            frames.add(message.getPayload());
            if (replyPong && message.getPayload().contains("\"type\":\"ping\"")) {
                session.sendMessage(new TextMessage("{\"type\":\"pong\"}"));
            }
        }

        @Override
        public void afterConnectionClosed(WebSocketSession session, CloseStatus status) {
            closeStatus.set(status);
        }
    }
}
