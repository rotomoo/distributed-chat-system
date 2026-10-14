package com.distributed.chat.system.chatting.web.controller.feign;

import static org.assertj.core.api.Assertions.assertThat;

import com.distributed.chat.system.common.event.ChatMessageEvent;
import com.distributed.chat.system.common.event.ChatReactionEvent;
import com.distributed.chat.system.common.event.EventJson;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import org.junit.jupiter.api.AfterAll;
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
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

/**
 * 실제 HTTP 서버(JDK HttpServer)를 프로듀서 대신 띄워서, Feign 이 Spring 기본 JSON 인코더로 이벤트를 보내는지 확인한다
 * (프로듀서는 이 본문을 그대로 Kafka 값으로 쓴다)
 */
@DisplayName("[통합] KafkaProducerClient 테스트")
@SpringBootTest(classes = KafkaProducerClientIntegrationTest.TestConfig.class, webEnvironment = SpringBootTest.WebEnvironment.NONE)
class KafkaProducerClientIntegrationTest {

    @EnableFeignClients(clients = KafkaProducerClient.class)
    @ImportAutoConfiguration({JacksonAutoConfiguration.class, HttpMessageConvertersAutoConfiguration.class, FeignAutoConfiguration.class})
    static class TestConfig {
    }

    private record ReceivedRequest(String path, String contentType, String body) {
    }

    private static final List<ReceivedRequest> RECEIVED = new CopyOnWriteArrayList<>();
    private static final HttpServer PRODUCER = startProducer();

    @DynamicPropertySource
    static void producerUrl(DynamicPropertyRegistry registry) {
        // 로드밸런싱 대신 이 주소로 (spring.cloud.openfeign.client.config.{name}.url)
        registry.add("spring.cloud.openfeign.client.config.kafka-producer.url", () -> "http://127.0.0.1:" + PRODUCER.getAddress().getPort());
    }

    @AfterAll
    static void stopProducer() {
        PRODUCER.stop(0);
    }

    @Autowired
    private KafkaProducerClient kafkaProducerClient;

    @BeforeEach
    void setup() {
        RECEIVED.clear();
    }

    @Test
    @DisplayName("success case : 메시지는 /v1/private/event/publish/chat-send-message 에 application/json 본문으로 보낸다")
    void 메시지는_application_json_본문으로_보낸다() {
        // given
        ChatMessageEvent message = new ChatMessageEvent("event-1", 5L, 7L, "안녕 😀", 1_700_000_000_000L, List.of("6acc66675f99715e286f0f6e"));

        // when
        kafkaProducerClient.publishChatSendMessage(message);

        // then
        assertThat(RECEIVED).hasSize(1);
        ReceivedRequest request = RECEIVED.get(0);
        assertThat(request.path()).isEqualTo("/v1/private/event/publish/chat-send-message");
        assertThat(request.contentType()).startsWith("application/json");
        assertThat(EventJson.read(request.body(), ChatMessageEvent.class)).isEqualTo(message);
        assertThat(request.body()).contains("\"eventId\":\"event-1\"", "\"channelId\":7", "\"attachmentFileIds\":[\"6acc66675f99715e286f0f6e\"]");
    }

    @Test
    @DisplayName("success case : 리액션은 /v1/private/event/publish/chat-reaction 에 action 이름(ADD / REMOVE) 그대로 보낸다")
    void 리액션은_action_이름_그대로_보낸다() {
        // given
        ChatReactionEvent reaction = new ChatReactionEvent(5L, 7L, "6acc66675f99715e286f0f6e", "👍", ChatReactionEvent.Action.REMOVE, 1L);

        // when
        kafkaProducerClient.publishChatReaction(reaction);

        // then
        assertThat(RECEIVED).hasSize(1);
        ReceivedRequest request = RECEIVED.get(0);
        assertThat(request.path()).isEqualTo("/v1/private/event/publish/chat-reaction");
        assertThat(request.contentType()).startsWith("application/json");
        assertThat(EventJson.read(request.body(), ChatReactionEvent.class)).isEqualTo(reaction);
        assertThat(request.body()).contains("\"action\":\"REMOVE\"");
    }

    private static HttpServer startProducer() {
        try {
            HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
            server.createContext("/", exchange -> {
                RECEIVED.add(new ReceivedRequest(exchange.getRequestURI().getPath(), exchange.getRequestHeaders().getFirst("Content-Type"),
                    new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8)));
                exchange.sendResponseHeaders(200, -1);
                exchange.close();
            });
            server.start();
            return server;
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
    }
}
