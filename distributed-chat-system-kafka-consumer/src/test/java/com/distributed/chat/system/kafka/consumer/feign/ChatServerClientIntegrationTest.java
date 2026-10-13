package com.distributed.chat.system.kafka.consumer.feign;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.distributed.chat.system.common.config.ProtobufConfig;
import com.sun.net.httpserver.HttpServer;
import feign.FeignException;
import feign.RetryableException;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.URI;
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
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.cloud.openfeign.EnableFeignClients;
import org.springframework.cloud.openfeign.FeignAutoConfiguration;
import org.springframework.context.annotation.Import;
import protobuf.EdaChatMessage.ChatMessage;
import protobuf.EdaChatMessage.ChatMessageDelivery;
import protobuf.EdaChatMessage.ChatReactionDelivery;
import protobuf.EdaChatMessage.Reaction;

/**
 * 실제 HTTP 서버(JDK HttpServer)를 채팅 서버 대신 띄워서, Feign 설정(대상 URI, protobuf 본문, 타임아웃, 재시도 없음)을 그대로 검증한다
 */
@DisplayName("[통합] ChatServerClient 테스트")
@SpringBootTest(classes = ChatServerClientIntegrationTest.TestConfig.class, webEnvironment = SpringBootTest.WebEnvironment.NONE)
class ChatServerClientIntegrationTest {

    @EnableFeignClients(clients = ChatServerClient.class)
    @ImportAutoConfiguration({HttpMessageConvertersAutoConfiguration.class, FeignAutoConfiguration.class})
    @Import(ProtobufConfig.class)
    static class TestConfig {
    }

    private record ReceivedRequest(String path, String contentType, byte[] body) {
    }

    @Autowired
    private ChatServerClient chatServerClient;

    private HttpServer chatServer;
    private ExecutorService chatServerExecutor;
    private final List<ReceivedRequest> receivedRequests = new CopyOnWriteArrayList<>();
    private volatile int responseStatus = 200;
    private volatile long responseDelayMs = 0;

    private final ChatMessageDelivery delivery = ChatMessageDelivery.newBuilder()
        .setMessageId("message-1")
        .setChatMessage(ChatMessage.newBuilder()
            .setSendUserId(5L)
            .setChannelId(7L)
            .setMessage("hello")
            .setPublishDt(1_700_000_000_000L)
            .build())
        .addAllReceiveUserIds(List.of(5L, 17L))
        .build();

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
                exchange.getRequestBody().readAllBytes()));
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
    @DisplayName("success case : 넘긴 URI 의 채팅 서버로 /internal/chat/deliver 에 protobuf 본문을 보낸다")
    void 넘긴_URI_의_채팅_서버로_protobuf_본문을_보낸다() throws Exception {
        // given
        URI chatServerUri = chatServerUri();

        // when
        chatServerClient.deliver(chatServerUri, delivery);

        // then
        assertThat(receivedRequests).hasSize(1);
        ReceivedRequest request = receivedRequests.get(0);
        assertThat(request.path()).isEqualTo("/internal/chat/deliver");
        assertThat(request.contentType()).startsWith("application/x-protobuf");
        assertThat(ChatMessageDelivery.parseFrom(request.body())).isEqualTo(delivery);
    }

    @Test
    @DisplayName("success case : 리액션은 같은 채팅 서버의 /internal/chat/deliver-reaction 에 protobuf 본문을 보낸다")
    void 리액션은_같은_채팅_서버의_deliver_reaction_에_protobuf_본문을_보낸다() throws Exception {
        // given
        ChatReactionDelivery reactionDelivery = ChatReactionDelivery.newBuilder()
            .setMessageId("message-1").setChannelId(7L)
            .addReactions(Reaction.newBuilder().setEmoji("👍").addAllUserIds(List.of(5L, 17L)).build())
            .addAllReceiveUserIds(List.of(5L, 17L))
            .build();

        // when
        chatServerClient.deliverReaction(chatServerUri(), reactionDelivery);

        // then
        assertThat(receivedRequests).hasSize(1);
        ReceivedRequest request = receivedRequests.get(0);
        assertThat(request.path()).isEqualTo("/internal/chat/deliver-reaction");
        assertThat(request.contentType()).startsWith("application/x-protobuf");
        assertThat(ChatReactionDelivery.parseFrom(request.body())).isEqualTo(reactionDelivery);
    }

    @Test
    @DisplayName("fail case : 리액션도 응답이 read timeout 보다 늦으면 같은 설정으로 1번만 보내고 실패한다")
    void 리액션도_응답이_read_timeout_보다_늦으면_같은_설정으로_1번만_보내고_실패한다() {
        // given
        responseDelayMs = 2_500;
        URI chatServerUri = chatServerUri();

        // when & then
        assertThatThrownBy(() -> chatServerClient.deliverReaction(chatServerUri, ChatReactionDelivery.getDefaultInstance()))
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
