package com.distributed.chat.system.kafka.consumer.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.client.ExpectedCount.once;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.cloud.client.DefaultServiceInstance;
import org.springframework.cloud.client.ServiceInstance;
import org.springframework.cloud.client.discovery.DiscoveryClient;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.SetOperations;
import org.springframework.http.HttpMethod;
import org.springframework.mock.http.client.MockClientHttpRequest;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.test.web.client.RequestMatcher;
import org.springframework.web.client.RestClient;
import protobuf.EdaChatMessage.ChatMessage;
import protobuf.EdaChatMessage.ChatMessageDelivery;

@DisplayName("[단위] ChatMessageDeliveryService 테스트")
@ExtendWith(MockitoExtension.class)
class ChatMessageDeliveryServiceUnitTest {

    @Mock
    private RedisTemplate<String, Object> redisTemplate;

    @Mock
    private SetOperations<String, Object> setOperations;

    @Mock
    private DiscoveryClient discoveryClient;

    private MockRestServiceServer chatServers;

    private ChatMessageDeliveryService chatMessageDeliveryService;

    private final ChatMessage chatMessage = ChatMessage.newBuilder()
        .setSendUserId(5L)
        .setChannelId(0L)
        .setMessage("hello")
        .setPublishDt(1_700_000_000_000L)
        .build();

    @BeforeEach
    void setup() {
        RestClient.Builder builder = RestClient.builder();
        chatServers = MockRestServiceServer.bindTo(builder).ignoreExpectOrder(true).build();
        chatMessageDeliveryService = new ChatMessageDeliveryService(redisTemplate, discoveryClient, builder);
    }

    @Test
    @DisplayName("success case : Eureka 의 채팅 서버마다 그 서버 접속자에게 한 번씩 전달한다")
    void 채팅_서버마다_그_서버_접속자에게_한_번씩_전달한다() {
        // arrange
        givenChatServers(chatServer("10.0.0.1", 1111), chatServer("10.0.0.2", 2222));
        when(setOperations.members("chatServerUri:10.0.0.1:1111")).thenReturn(Set.of("5", "17"));
        when(setOperations.members("chatServerUri:10.0.0.2:2222")).thenReturn(Set.of("9"));
        expectDelivery("http://10.0.0.1:1111/internal/chat/deliver", List.of(5L, 17L)).andRespond(withSuccess());
        expectDelivery("http://10.0.0.2:2222/internal/chat/deliver", List.of(9L)).andRespond(withSuccess());

        // act
        chatMessageDeliveryService.deliver("message-1", chatMessage);

        // assert
        chatServers.verify();
    }

    @Test
    @DisplayName("success case : 접속자가 없는 채팅 서버에는 보내지 않는다")
    void 접속자가_없는_채팅_서버에는_보내지_않는다() {
        // arrange
        givenChatServers(chatServer("10.0.0.1", 1111), chatServer("10.0.0.2", 2222));
        when(setOperations.members("chatServerUri:10.0.0.1:1111")).thenReturn(Set.of());
        when(setOperations.members("chatServerUri:10.0.0.2:2222")).thenReturn(Set.of("9"));
        expectDelivery("http://10.0.0.2:2222/internal/chat/deliver", List.of(9L)).andRespond(withSuccess());

        // act
        chatMessageDeliveryService.deliver("message-1", chatMessage);

        // assert : 10.0.0.1 로 요청이 갔다면 MockRestServiceServer 가 예상하지 못한 요청으로 실패시킨다
        chatServers.verify();
    }

    @Test
    @DisplayName("success case : 한 채팅 서버 전달이 실패해도 나머지 서버에는 전달한다")
    void 한_채팅_서버_전달이_실패해도_나머지_서버에는_전달한다() {
        // arrange
        givenChatServers(chatServer("10.0.0.1", 1111), chatServer("10.0.0.2", 2222));
        when(setOperations.members("chatServerUri:10.0.0.1:1111")).thenReturn(Set.of("5"));
        when(setOperations.members("chatServerUri:10.0.0.2:2222")).thenReturn(Set.of("9"));
        expectDelivery("http://10.0.0.1:1111/internal/chat/deliver", List.of(5L)).andRespond(withServerError());
        expectDelivery("http://10.0.0.2:2222/internal/chat/deliver", List.of(9L)).andRespond(withSuccess());

        // act
        chatMessageDeliveryService.deliver("message-1", chatMessage);

        // assert
        chatServers.verify();
    }

    @Test
    @DisplayName("success case : 살아있는 채팅 서버가 없으면 아무 데도 보내지 않는다")
    void 살아있는_채팅_서버가_없으면_아무_데도_보내지_않는다() {
        // arrange
        when(discoveryClient.getInstances("chatting")).thenReturn(List.of());

        // act
        chatMessageDeliveryService.deliver("message-1", chatMessage);

        // assert
        chatServers.verify();
    }

    private void givenChatServers(ServiceInstance... instances) {
        when(discoveryClient.getInstances("chatting")).thenReturn(List.of(instances));
        when(redisTemplate.opsForSet()).thenReturn(setOperations);
    }

    private ServiceInstance chatServer(String host, int port) {
        return new DefaultServiceInstance(host + ":" + port, "chatting", host, port, false);
    }

    private org.springframework.test.web.client.ResponseActions expectDelivery(String url, List<Long> receiveUserIds) {
        return chatServers.expect(once(), requestTo(url))
            .andExpect(method(HttpMethod.POST))
            .andExpect(content().contentTypeCompatibleWith("application/x-protobuf"))
            .andExpect(deliveryOf(receiveUserIds));
    }

    private RequestMatcher deliveryOf(List<Long> receiveUserIds) {
        return request -> {
            ChatMessageDelivery delivery = ChatMessageDelivery.parseFrom(
                ((MockClientHttpRequest) request).getBodyAsBytes());
            assertThat(delivery.getMessageId()).isEqualTo("message-1");
            assertThat(delivery.getChatMessage()).isEqualTo(chatMessage);
            assertThat(delivery.getReceiveUserIdsList()).containsExactlyInAnyOrderElementsOf(receiveUserIds);
        };
    }
}
