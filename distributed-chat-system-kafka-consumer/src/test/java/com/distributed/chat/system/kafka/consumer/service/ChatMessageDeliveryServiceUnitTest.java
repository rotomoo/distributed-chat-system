package com.distributed.chat.system.kafka.consumer.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowableOfType;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.distributed.chat.system.kafka.consumer.feign.ChatServerClient;
import com.distributed.chat.system.mongodb.collection.ChatMessage.Attachment;
import com.distributed.chat.system.mongodb.collection.ChatMessage.Reaction;
import java.net.URI;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.cloud.client.DefaultServiceInstance;
import org.springframework.cloud.client.ServiceInstance;
import org.springframework.cloud.client.discovery.DiscoveryClient;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.SetOperations;
import protobuf.EdaChatMessage.ChatMessage;
import protobuf.EdaChatMessage.ChatMessageDelivery;
import protobuf.EdaChatMessage.ChatReactionDelivery;

@DisplayName("[단위] ChatMessageDeliveryService 테스트")
@ExtendWith(MockitoExtension.class)
class ChatMessageDeliveryServiceUnitTest {

    private static final URI CHAT_SERVER_1 = URI.create("http://10.0.0.1:1111");
    private static final URI CHAT_SERVER_2 = URI.create("http://10.0.0.2:2222");

    @Mock
    private RedisTemplate<String, Object> redisTemplate;

    @Mock
    private SetOperations<String, Object> setOperations;

    @Mock
    private DiscoveryClient discoveryClient;

    @Mock
    private ChatServerClient chatServerClient;

    @InjectMocks
    private ChatMessageDeliveryService chatMessageDeliveryService;

    private final ChatMessage chatMessage = ChatMessage.newBuilder()
        .setSendUserId(5L)
        .setChannelId(7L)
        .setMessage("hello")
        .setPublishDt(1_700_000_000_000L)
        .build();

    @Test
    @DisplayName("success case : 채팅 서버마다 그 서버 접속자 중 채널 멤버에게만 한 번씩 전달한다")
    void 채팅_서버마다_그_서버_접속자_중_채널_멤버에게만_한_번씩_전달한다() {
        // arrange
        givenChatServers(chatServer("10.0.0.1", 1111), chatServer("10.0.0.2", 2222));
        when(setOperations.intersect("chatServerUri:10.0.0.1:1111", "channel:7:members")).thenReturn(Set.of("5", "17"));
        when(setOperations.intersect("chatServerUri:10.0.0.2:2222", "channel:7:members")).thenReturn(Set.of("9"));

        // act
        chatMessageDeliveryService.deliver("message-1", chatMessage, List.of());

        // assert
        assertDelivered(CHAT_SERVER_1, List.of(5L, 17L));
        assertDelivered(CHAT_SERVER_2, List.of(9L));
    }

    @Test
    @DisplayName("success case : 채널 멤버가 접속하지 않은 채팅 서버에는 보내지 않는다")
    void 채널_멤버가_접속하지_않은_채팅_서버에는_보내지_않는다() {
        // arrange
        givenChatServers(chatServer("10.0.0.1", 1111), chatServer("10.0.0.2", 2222));
        when(setOperations.intersect("chatServerUri:10.0.0.1:1111", "channel:7:members")).thenReturn(Set.of());
        when(setOperations.intersect("chatServerUri:10.0.0.2:2222", "channel:7:members")).thenReturn(Set.of("9"));

        // act
        chatMessageDeliveryService.deliver("message-1", chatMessage, List.of());

        // assert
        verify(chatServerClient, never()).deliver(eq(CHAT_SERVER_1), any());
        assertDelivered(CHAT_SERVER_2, List.of(9L));
    }

    @Test
    @DisplayName("fail case : 한 채팅 서버 전달이 실패해도 나머지 서버에는 전달하고, 끝에서 실패한 서버를 담아 예외를 던진다 (재시도 토픽으로)")
    void 한_채팅_서버_전달이_실패해도_나머지_서버에는_전달하고_끝에서_예외를_던진다() {
        // arrange
        givenChatServers(chatServer("10.0.0.1", 1111), chatServer("10.0.0.2", 2222));
        when(setOperations.intersect("chatServerUri:10.0.0.1:1111", "channel:7:members")).thenReturn(Set.of("5"));
        when(setOperations.intersect("chatServerUri:10.0.0.2:2222", "channel:7:members")).thenReturn(Set.of("9"));
        doThrow(new RuntimeException("timeout")).when(chatServerClient).deliver(eq(CHAT_SERVER_1), any());

        // act
        DeliveryFailedException exception = catchThrowableOfType(
            () -> chatMessageDeliveryService.deliver("message-1", chatMessage, List.of()), DeliveryFailedException.class);

        // assert
        assertDelivered(CHAT_SERVER_2, List.of(9L));
        assertThat(exception.getFailedChatServers()).containsExactly("10.0.0.1:1111");
    }

    @Test
    @DisplayName("success case : 살아있는 채팅 서버가 없으면 아무 데도 보내지 않는다")
    void 살아있는_채팅_서버가_없으면_아무_데도_보내지_않는다() {
        // arrange
        when(discoveryClient.getInstances("chatting")).thenReturn(List.of());

        // act
        chatMessageDeliveryService.deliver("message-1", chatMessage, List.of());

        // assert
        verify(chatServerClient, never()).deliver(any(), any());
    }

    @Test
    @DisplayName("success case : 확인된 첨부를 메시지와 함께 전달한다")
    void 확인된_첨부를_메시지와_함께_전달한다() {
        // arrange
        givenChatServers(chatServer("10.0.0.1", 1111));
        when(setOperations.intersect("chatServerUri:10.0.0.1:1111", "channel:7:members")).thenReturn(Set.of("5"));

        // act
        chatMessageDeliveryService.deliver("message-1", chatMessage,
            List.of(new Attachment("6acc66675f99715e286f0f6e", "사진.png", "image/png", 10L)));

        // assert
        ArgumentCaptor<ChatMessageDelivery> captor = ArgumentCaptor.forClass(ChatMessageDelivery.class);
        verify(chatServerClient).deliver(eq(CHAT_SERVER_1), captor.capture());
        assertThat(captor.getValue().getAttachmentsList()).hasSize(1);
        assertThat(captor.getValue().getAttachments(0).getFileId()).isEqualTo("6acc66675f99715e286f0f6e");
        assertThat(captor.getValue().getAttachments(0).getName()).isEqualTo("사진.png");
        assertThat(captor.getValue().getAttachments(0).getContentType()).isEqualTo("image/png");
        assertThat(captor.getValue().getAttachments(0).getSize()).isEqualTo(10L);
    }

    @Test
    @DisplayName("success case : 리액션은 메시지와 같은 방법으로 고른 수신자에게 reactions 전체를 서버당 한 번 전달한다")
    void 리액션은_메시지와_같은_방법으로_고른_수신자에게_reactions_전체를_서버당_한_번_전달한다() {
        // arrange
        givenChatServers(chatServer("10.0.0.1", 1111), chatServer("10.0.0.2", 2222));
        when(setOperations.intersect("chatServerUri:10.0.0.1:1111", "channel:7:members")).thenReturn(Set.of("5", "17"));
        when(setOperations.intersect("chatServerUri:10.0.0.2:2222", "channel:7:members")).thenReturn(Set.of());

        // act
        chatMessageDeliveryService.deliverReaction(7L, "message-1",
            List.of(new Reaction("👍", List.of(5L, 17L)), new Reaction("🎉", List.of(5L))));

        // assert
        ArgumentCaptor<ChatReactionDelivery> captor = ArgumentCaptor.forClass(ChatReactionDelivery.class);
        verify(chatServerClient).deliverReaction(eq(CHAT_SERVER_1), captor.capture());
        verify(chatServerClient, never()).deliverReaction(eq(CHAT_SERVER_2), any());
        ChatReactionDelivery delivery = captor.getValue();
        assertThat(delivery.getMessageId()).isEqualTo("message-1");
        assertThat(delivery.getChannelId()).isEqualTo(7L);
        assertThat(delivery.getReceiveUserIdsList()).containsExactlyInAnyOrder(5L, 17L);
        assertThat(delivery.getReactionsList()).extracting(r -> r.getEmoji()).containsExactly("👍", "🎉");
        assertThat(delivery.getReactions(0).getUserIdsList()).containsExactly(5L, 17L);
    }

    @Test
    @DisplayName("fail case : 리액션 전달이 한 서버에서 실패해도 나머지 서버에는 전달하고 끝에서 예외를 던진다")
    void 리액션_전달이_한_서버에서_실패해도_나머지_서버에는_전달하고_끝에서_예외를_던진다() {
        // arrange
        givenChatServers(chatServer("10.0.0.1", 1111), chatServer("10.0.0.2", 2222));
        when(setOperations.intersect("chatServerUri:10.0.0.1:1111", "channel:7:members")).thenReturn(Set.of("5"));
        when(setOperations.intersect("chatServerUri:10.0.0.2:2222", "channel:7:members")).thenReturn(Set.of("9"));
        doThrow(new RuntimeException("timeout")).when(chatServerClient).deliverReaction(eq(CHAT_SERVER_1), any());

        // act
        DeliveryFailedException exception = catchThrowableOfType(
            () -> chatMessageDeliveryService.deliverReaction(7L, "message-1", List.of()), DeliveryFailedException.class);

        // assert
        verify(chatServerClient).deliverReaction(eq(CHAT_SERVER_2), any());
        assertThat(exception.getFailedChatServers()).containsExactly("10.0.0.1:1111");
    }

    private void givenChatServers(ServiceInstance... instances) {
        when(discoveryClient.getInstances("chatting")).thenReturn(List.of(instances));
        when(redisTemplate.opsForSet()).thenReturn(setOperations);
    }

    private ServiceInstance chatServer(String host, int port) {
        return new DefaultServiceInstance(host + ":" + port, "chatting", host, port, false);
    }

    private void assertDelivered(URI chatServerUri, List<Long> receiveUserIds) {
        ArgumentCaptor<ChatMessageDelivery> captor = ArgumentCaptor.forClass(ChatMessageDelivery.class);
        verify(chatServerClient).deliver(eq(chatServerUri), captor.capture());
        ChatMessageDelivery delivery = captor.getValue();
        assertThat(delivery.getMessageId()).isEqualTo("message-1");
        assertThat(delivery.getChatMessage()).isEqualTo(chatMessage);
        assertThat(delivery.getReceiveUserIdsList()).containsExactlyInAnyOrderElementsOf(receiveUserIds);
    }
}
