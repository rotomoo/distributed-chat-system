package com.distributed.chat.system.chatting.web.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import com.distributed.chat.system.common.event.ChatMessageDelivery;
import com.distributed.chat.system.common.event.ChatMessageDelivery.Attachment;
import com.distributed.chat.system.common.event.ChatMessageEvent;
import com.distributed.chat.system.common.event.ChatReactionDelivery;
import com.distributed.chat.system.common.event.ChatReactionDelivery.Reaction;
import com.distributed.chat.system.common.event.EventJson;
import com.distributed.chat.system.common.redis.ChatPresenceKeys;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;
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
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketHttpHeaders;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.client.standard.StandardWebSocketClient;
import org.springframework.web.socket.handler.TextWebSocketHandler;

/**
 * 진짜 채팅 서버(랜덤 포트) + 진짜 웹소켓 클라이언트 + 로컬 Redis. 컨슈머가 보내는 것과 같은 JSON 본문을 내부 전달 API 로 보내고
 * 브라우저가 받는 프레임이 예전과 같은 모양인지 확인한다
 */
@DisplayName("[통합] 채팅 서버 내부 전달 API (JSON) → 웹소켓 프레임 테스트")
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = {
    "eureka.client.enabled=false"
})
class ChatDeliveryIntegrationTest {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
    private static final Duration WAIT = Duration.ofSeconds(5);

    @LocalServerPort
    private int port;

    @Autowired
    @SuppressWarnings("rawtypes")
    private SessionRepository sessionRepository;

    @Autowired
    private RedisTemplate<String, Object> redisTemplate;

    private final HttpClient httpClient = HttpClient.newHttpClient();
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
    @DisplayName("success case : JSON 메시지 전달을 받으면 수신자의 브라우저에 예전과 같은 message 프레임을 보낸다")
    void JSON_메시지_전달을_받으면_예전과_같은_message_프레임을_보낸다() throws Exception {
        // given
        String userId = newUserId();
        Client browser = connect(userId);
        ChatMessageDelivery delivery = new ChatMessageDelivery("6acc66675f99715e286f0f70",
            new ChatMessageEvent("event-1", 9L, 7L, "점심 뭐 먹을까요?", 1_700_000_000_000L, List.of("6acc66675f99715e286f0f6e")),
            List.of(Long.parseLong(userId)),
            List.of(new Attachment("6acc66675f99715e286f0f6e", "사진.png", "image/png", 10L)));

        // when
        int status = post("/internal/chat/deliver", EventJson.write(delivery));

        // then
        assertThat(status).isEqualTo(200);
        await().atMost(WAIT).until(() -> browser.frames("message").size() == 1);
        Map<String, Object> expected = new LinkedHashMap<>();
        expected.put("type", "message");
        expected.put("messageId", "6acc66675f99715e286f0f70");
        expected.put("channelId", 7);
        expected.put("sendUserId", 9);
        expected.put("message", "점심 뭐 먹을까요?");
        expected.put("publishDt", 1_700_000_000_000L);
        expected.put("attachments", List.of(Map.of("fileId", "6acc66675f99715e286f0f6e", "name", "사진.png", "contentType", "image/png",
            "size", 10)));
        assertThat(browser.frames("message").get(0)).isEqualTo(expected);
        // 키 순서도 예전 그대로 (type, messageId, channelId, sendUserId, message, publishDt, attachments)
        assertThat(new ArrayList<>(browser.frames("message").get(0).keySet())).containsExactlyElementsOf(expected.keySet());
    }

    @Test
    @DisplayName("success case : JSON 리액션 전달을 받으면 수신자의 브라우저에 예전과 같은 reaction 프레임을 보낸다")
    void JSON_리액션_전달을_받으면_예전과_같은_reaction_프레임을_보낸다() throws Exception {
        // given
        String userId = newUserId();
        Client browser = connect(userId);
        ChatReactionDelivery delivery = new ChatReactionDelivery("6acc66675f99715e286f0f70", 7L,
            List.of(new Reaction("👍", List.of(5L, 9L)), new Reaction("🎉", List.of(9L))), List.of(Long.parseLong(userId)));

        // when
        int status = post("/internal/chat/deliver-reaction", EventJson.write(delivery));

        // then
        assertThat(status).isEqualTo(200);
        await().atMost(WAIT).until(() -> browser.frames("reaction").size() == 1);
        assertThat(browser.frames("reaction").get(0)).isEqualTo(Map.of(
            "type", "reaction",
            "channelId", 7,
            "messageId", "6acc66675f99715e286f0f70",
            "reactions", List.of(Map.of("emoji", "👍", "userIds", List.of(5, 9)), Map.of("emoji", "🎉", "userIds", List.of(9)))));
    }

    @Test
    @DisplayName("success case : 모르는 필드가 있는 본문(새 컨슈머)도 받고, 없는 필드는 기본값으로 프레임을 만든다")
    void 모르는_필드가_있는_본문도_받고_없는_필드는_기본값으로_프레임을_만든다() throws Exception {
        // given : 첨부 없음, publishDt 없음, 모르는 필드 여러 곳
        String userId = newUserId();
        Client browser = connect(userId);
        String body = "{\"messageId\":\"m-1\",\"priority\":\"high\",\"chatMessage\":{\"sendUserId\":9,\"channelId\":7,\"message\":\"hi\","
            + "\"threadId\":\"t-1\"},\"receiveUserIds\":[" + userId + "]}";

        // when
        int status = post("/internal/chat/deliver", body);

        // then
        assertThat(status).isEqualTo(200);
        await().atMost(WAIT).until(() -> browser.frames("message").size() == 1);
        Map<String, Object> frame = browser.frames("message").get(0);
        assertThat(frame.get("message")).isEqualTo("hi");
        assertThat(frame.get("publishDt")).isEqualTo(0);
        assertThat(frame.get("attachments")).isEqualTo(List.of());
        assertThat(frame).doesNotContainKeys("priority", "threadId");
    }

    @Test
    @DisplayName("fail case : JSON 이 아닌 본문은 400 이고 브라우저에는 아무것도 보내지 않는다")
    void JSON_이_아닌_본문은_400_이고_브라우저에는_아무것도_보내지_않는다() throws Exception {
        // given
        String userId = newUserId();
        Client browser = connect(userId);

        // when
        int status = post("/internal/chat/deliver", "{\"messageId\":");

        // then
        assertThat(status).isEqualTo(400);
        TimeUnit.MILLISECONDS.sleep(300);
        assertThat(browser.frames("message")).isEmpty();
    }

    private int post(String path, String json) throws Exception {
        HttpRequest request = HttpRequest.newBuilder(URI.create("http://localhost:" + port + path))
            .header("Content-Type", "application/json")
            .POST(HttpRequest.BodyPublishers.ofString(json, StandardCharsets.UTF_8))
            .build();
        return httpClient.send(request, HttpResponse.BodyHandlers.discarding()).statusCode();
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
        // 접속 정보 기록까지 끝난 뒤에 전달해야 받는다
        await().atMost(WAIT).until(() -> !client.frames("connected").isEmpty());
        return client;
    }

    private static final class Client extends TextWebSocketHandler {

        private WebSocketSession session;
        private final List<String> frames = new CopyOnWriteArrayList<>();

        /**
         * 받은 프레임 중 그 type 만 (JSON 을 그대로 Map 으로, 키 순서 유지)
         */
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> frames(String type) {
            List<Map<String, Object>> matched = new ArrayList<>();
            for (String frame : frames) {
                try {
                    Map<String, Object> parsed = OBJECT_MAPPER.readValue(frame, LinkedHashMap.class);
                    if (type.equals(parsed.get("type"))) {
                        matched.add(parsed);
                    }
                } catch (Exception e) {
                    throw new IllegalStateException(e);
                }
            }
            return matched;
        }

        @Override
        protected void handleTextMessage(WebSocketSession session, TextMessage message) {
            frames.add(message.getPayload());
        }
    }
}
