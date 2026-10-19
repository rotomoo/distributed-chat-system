package com.distributed.chat.system.message.consumer.feign;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.distributed.chat.system.common.event.ChatMessageDelivery;
import com.distributed.chat.system.common.event.ChatMessageEvent;
import com.distributed.chat.system.common.event.ChatReactionDelivery;
import com.distributed.chat.system.common.event.ChatReactionDelivery.Reaction;
import com.distributed.chat.system.common.event.EventJson;
import com.sun.net.httpserver.HttpServer;
import feign.FeignException;
import feign.RetryableException;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.ImportAutoConfiguration;
import org.springframework.boot.autoconfigure.http.HttpMessageConvertersAutoConfiguration;
import org.springframework.boot.autoconfigure.jackson.JacksonAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.cloud.openfeign.EnableFeignClients;
import org.springframework.cloud.openfeign.FeignAutoConfiguration;

/**
 * 실제 HTTP 서버(JDK HttpServer)를 채팅 서버 대신 띄워서, Feign 설정(대상 URI, JSON 본문, 타임아웃, 재시도 없음)을 그대로 검증한다
 */
@DisplayName("[통합] ChatServerClient 테스트")
@SpringBootTest(classes = ChatServerClientIntegrationTest.TestConfig.class, webEnvironment = SpringBootTest.WebEnvironment.NONE)
class ChatServerClientIntegrationTest {

    @EnableFeignClients(clients = ChatServerClient.class)
    @ImportAutoConfiguration({JacksonAutoConfiguration.class, HttpMessageConvertersAutoConfiguration.class, FeignAutoConfiguration.class})
    static class TestConfig {
    }

    private record ReceivedRequest(String path, String contentType, String body) {
    }

    @Autowired
    private ChatServerClient chatServerClient;

    private HttpServer chatServer;
    private ExecutorService chatServerExecutor;
    private final List<ReceivedRequest> receivedRequests = new CopyOnWriteArrayList<>();
    private volatile int responseStatus = 200;
    private volatile long responseDelayMs = 0;

    private final ChatMessageDelivery delivery = new ChatMessageDelivery("message-1",
        new ChatMessageEvent("event-1", 5L, 7L, "hello 😀", 1_700_000_000_000L, List.of()),
        List.of(5L, 17L),
        List.of(new ChatMessageDelivery.Attachment("6acc66675f99715e286f0f6e", "사진.png", "image/png", 10L)));

    @BeforeEach
    void setup() throws IOException {
        chatServer = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        // 늦게 응답하는 요청이 다음 재시도를 막지 않도록 요청마다 스레드를 쓴다
        chatServerExecutor = Executors.newCachedThreadPool();
        chatServer.setExecutor(chatServerExecutor);
        chatServer.createContext("/", exchange -> {
            receivedRequests.add(new ReceivedRequest(
                exchange.getRequestURI().getPath(),
                exchange.getRequestHeaders().getFirst("Content-Type"),
                new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8)));
            try {
                Thread.sleep(responseDelayMs);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            exchange.sendResponseHeaders(responseStatus, -1);
            exchange.close();
        });
        chatServer.start();
    }

    @AfterEach
    void tearDown() {
        chatServer.stop(0);
        chatServerExecutor.shutdownNow();
    }

    @Test
    @DisplayName("success case : 넘긴 URI 의 채팅 서버로 /internal/chat/deliver 에 JSON 본문을 보낸다")
    void 넘긴_URI_의_채팅_서버로_JSON_본문을_보낸다() {
        // given
        URI chatServerUri = chatServerUri();

        // when
        chatServerClient.deliver(chatServerUri, delivery);

        // then
        assertThat(receivedRequests).hasSize(1);
        ReceivedRequest request = receivedRequests.get(0);
        assertThat(request.path()).isEqualTo("/internal/chat/deliver");
        assertThat(request.contentType()).startsWith("application/json");
        assertThat(EventJson.read(request.body(), ChatMessageDelivery.class)).isEqualTo(delivery);
        assertThat(request.body()).contains("\"messageId\":\"message-1\"", "\"receiveUserIds\":[5,17]",
            "\"chatMessage\":{\"eventId\":\"event-1\"", "\"attachments\":[{\"fileId\":\"6acc66675f99715e286f0f6e\"");
    }

    @Test
    @DisplayName("success case : 리액션은 같은 채팅 서버의 /internal/chat/deliver-reaction 에 JSON 본문을 보낸다")
    void 리액션은_같은_채팅_서버의_deliver_reaction_에_JSON_본문을_보낸다() {
        // given
        ChatReactionDelivery reactionDelivery = new ChatReactionDelivery("message-1", 7L,
            List.of(new Reaction("👍", List.of(5L, 17L))), List.of(5L, 17L));

        // when
        chatServerClient.deliverReaction(chatServerUri(), reactionDelivery);

        // then
        assertThat(receivedRequests).hasSize(1);
        ReceivedRequest request = receivedRequests.get(0);
        assertThat(request.path()).isEqualTo("/internal/chat/deliver-reaction");
        assertThat(request.contentType()).startsWith("application/json");
        assertThat(EventJson.read(request.body(), ChatReactionDelivery.class)).isEqualTo(reactionDelivery);
        // 이모지는 인코더에 따라 그대로 또는 유니코드 이스케이프로 간다 (어느 쪽이든 같은 JSON 문자열)
        assertThat(request.body()).contains("\"reactions\":[{\"emoji\":", "\"userIds\":[5,17]}]", "\"receiveUserIds\":[5,17]");
    }

    @Test
    @DisplayName("fail case : 리액션도 응답이 read timeout 보다 늦으면 같은 설정으로 1번만 보내고 실패한다")
    void 리액션도_응답이_read_timeout_보다_늦으면_같은_설정으로_1번만_보내고_실패한다() {
        // given
        responseDelayMs = 2_500;
        URI chatServerUri = chatServerUri();

        // when & then
        assertThatThrownBy(() -> chatServerClient.deliverReaction(chatServerUri, new ChatReactionDelivery(null, 0L, null, null)))
            .isInstanceOf(RetryableException.class);
        assertThat(receivedRequests).hasSize(1);
    }

    @Test
    @DisplayName("fail case : 응답이 read timeout(2초)보다 늦으면 재시도 없이 1번만 보내고 실패한다 (재시도는 Kafka 재시도 토픽 몫)")
    void 응답이_read_timeout_보다_늦으면_재시도_없이_1번만_보내고_실패한다() {
        // given
        responseDelayMs = 2_500;
        URI chatServerUri = chatServerUri();
        long start = System.currentTimeMillis();

        // when & then
        assertThatThrownBy(() -> chatServerClient.deliver(chatServerUri, delivery))
            .isInstanceOf(RetryableException.class);
        long elapsedMs = System.currentTimeMillis() - start;
        assertThat(receivedRequests).hasSize(1);
        // 타임아웃 2초 × 1번. 타임아웃이 늘거나 Feign 재시도가 다시 생기면 여기서 걸린다
        assertThat(elapsedMs).isBetween(2_000L, 3_000L);
    }

    @Test
    @DisplayName("fail case : 채팅 서버가 5xx 를 주면 재시도하지 않고 바로 실패한다")
    void 채팅_서버가_5xx_를_주면_재시도하지_않고_바로_실패한다() {
        // given
        responseStatus = 500;
        URI chatServerUri = chatServerUri();

        // when & then
        assertThatThrownBy(() -> chatServerClient.deliver(chatServerUri, delivery))
            .isInstanceOf(FeignException.InternalServerError.class);
        assertThat(receivedRequests).hasSize(1);
    }

    @Test
    @DisplayName("fail case : 연결이 안 되는 서버면 기다리지 않고 바로 실패한다 (connect timeout 500ms 안)")
    void 연결이_안_되는_서버면_기다리지_않고_바로_실패한다() throws IOException {
        // given : 비어 있는 포트를 골라 아무도 듣지 않게 둔다
        int closedPort;
        try (ServerSocket socket = new ServerSocket(0)) {
            closedPort = socket.getLocalPort();
        }
        URI closedServerUri = URI.create("http://127.0.0.1:" + closedPort);
        long start = System.currentTimeMillis();

        // when & then
        assertThatThrownBy(() -> chatServerClient.deliver(closedServerUri, delivery))
            .isInstanceOf(RetryableException.class);
        assertThat(System.currentTimeMillis() - start).isLessThan(1_000L);
    }

    private URI chatServerUri() {
        return URI.create("http://127.0.0.1:" + chatServer.getAddress().getPort());
    }
}
