package com.distributed.chat.system.message.consumer.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowableOfType;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.distributed.chat.system.message.consumer.feign.ChatServerClient;
import com.distributed.chat.system.common.event.ChatMessageDelivery;
import com.distributed.chat.system.common.event.ChatMessageEvent;
import com.distributed.chat.system.common.event.ChatReactionDelivery;
import com.distributed.chat.system.mongodb.collection.ChatMessage.Attachment;
import com.distributed.chat.system.mongodb.collection.ChatMessage.Reaction;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.LongStream;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.cloud.client.DefaultServiceInstance;
import org.springframework.cloud.client.ServiceInstance;
import org.springframework.cloud.client.discovery.DiscoveryClient;
import org.springframework.data.redis.connection.RedisConnection;
import org.springframework.data.redis.connection.RedisStringCommands;
import org.springframework.data.redis.core.RedisCallback;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.data.redis.serializer.StringRedisSerializer;

@DisplayName("[단위] ChatMessageDeliveryService 테스트")
@ExtendWith(MockitoExtension.class)
class ChatMessageDeliveryServiceUnitTest {

    private static final String SERVER_1 = "10.0.0.1:1111";
    private static final String SERVER_2 = "10.0.0.2:2222";
    private static final URI CHAT_SERVER_1 = URI.create("http://" + SERVER_1);
    private static final URI CHAT_SERVER_2 = URI.create("http://" + SERVER_2);

    @Mock
    private RedisTemplate<String, Object> redisTemplate;

    @Mock
    private ValueOperations<String, Object> valueOperations;

    @Mock
    private DiscoveryClient discoveryClient;

    @Mock
    private ChatServerClient chatServerClient;

    private ExecutorService executor;

    private ChatMessageDeliveryService chatMessageDeliveryService;

    private final ChatMessageEvent chatMessage = new ChatMessageEvent("event-1", 5L, 7L, "hello", 1_700_000_000_000L, List.of());

    @BeforeEach
    void setup() {
        executor = Executors.newFixedThreadPool(4);
        lenient().when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        givenAliveChatServers(chatServer("10.0.0.1", 1111), chatServer("10.0.0.2", 2222));
        chatMessageDeliveryService = new ChatMessageDeliveryService(redisTemplate, discoveryClient, chatServerClient, executor);
    }

    @AfterEach
    void tearDown() {
        executor.shutdownNow();
    }

    @Test
    @DisplayName("success case : 멤버 접속 위치를 MGET 한 번으로 읽어 채팅 서버별로 묶어 서버당 한 번씩 보낸다")
    void 멤버_접속_위치를_MGET_한_번으로_읽어_채팅_서버별로_묶어_서버당_한_번씩_보낸다() {
        // arrange
        givenLocations(List.of("5", "9", "17"), at(SERVER_1), at(SERVER_2), at(SERVER_1));

        // act
        chatMessageDeliveryService.deliver("message-1", chatMessage, List.of(), chatMessageDeliveryService.locate(List.of(5L, 9L, 17L)));

        // assert
        assertDelivered(CHAT_SERVER_1, List.of(List.of(5L, 17L)));
        assertDelivered(CHAT_SERVER_2, List.of(List.of(9L)));
        verify(valueOperations, times(1)).multiGet(any());
    }

    @Test
    @DisplayName("success case : 접속 위치가 없거나(앱 꺼짐) 죽은 서버(Eureka 에 없음)를 가리키면 오프라인이라 보내지 않는다")
    void 접속_위치가_없거나_죽은_서버를_가리키면_오프라인이라_보내지_않는다() {
        // arrange
        givenLocations(List.of("5", "9", "17"), at(SERVER_1), null, at("10.0.0.9:9999"));

        // act
        chatMessageDeliveryService.deliver("message-1", chatMessage, List.of(), chatMessageDeliveryService.locate(List.of(5L, 9L, 17L)));

        // assert
        assertDelivered(CHAT_SERVER_1, List.of(List.of(5L)));
        verify(chatServerClient, never()).deliver(eq(CHAT_SERVER_2), any());
        verify(chatServerClient, never()).deliver(eq(URI.create("http://10.0.0.9:9999")), any());
    }

    @Test
    @DisplayName("success case : 접속 위치가 없거나 죽은 서버를 가리키는 멤버는 오프라인 목록으로 돌려준다 (알림 대상)")
    void 접속_위치가_없거나_죽은_서버를_가리키는_멤버는_오프라인_목록으로_돌려준다() {
        // arrange
        givenLocations(List.of("5", "9", "17", "21"), at(SERVER_1), null, at("10.0.0.9:9999"), at(SERVER_2));

        // act
        ChatReceivers receivers = chatMessageDeliveryService.locate(List.of(5L, 9L, 17L, 21L));

        // assert
        assertThat(receivers.offline()).containsExactly(9L, 17L);
        assertThat(receivers.byChatServer()).containsOnlyKeys(SERVER_1, SERVER_2);
        assertThat(receivers.byChatServer().get(SERVER_1)).containsExactly(5L);
        assertThat(receivers.byChatServer().get(SERVER_2)).containsExactly(21L);
        verify(chatServerClient, never()).deliver(any(), any());
    }

    @Test
    @DisplayName("success case : away 인 멤버도 접속해 있으므로 보내고, 예전 값(서버 주소 문자열)도 그 서버로 보낸다")
    void away_인_멤버도_보내고_예전_값도_그_서버로_보낸다() {
        // arrange : 5 = away, 9 = 예전 값 "ws://...", 17 = 알아볼 수 없는 값 (오프라인)
        givenLocations(List.of("5", "9", "17"),
            Map.of("server", "ws://" + SERVER_1, "status", "away"), "ws://" + SERVER_2, Map.of("status", "online"));

        // act
        ChatReceivers receivers = chatMessageDeliveryService.locate(List.of(5L, 9L, 17L));

        // assert
        assertThat(receivers.byChatServer().get(SERVER_1)).containsExactly(5L);
        assertThat(receivers.byChatServer().get(SERVER_2)).containsExactly(9L);
        assertThat(receivers.offline()).containsExactly(17L);
    }

    @Test
    @DisplayName("success case : 멤버가 없으면 Redis 를 보지 않고 빈 결과를 돌려준다")
    void 멤버가_없으면_Redis_를_보지_않고_빈_결과를_돌려준다() {
        // act
        ChatReceivers receivers = chatMessageDeliveryService.locate(List.of());

        // assert
        assertThat(receivers.offline()).isEmpty();
        assertThat(receivers.byChatServer()).isEmpty();
        verify(valueOperations, never()).multiGet(any());
    }

    @Test
    @DisplayName("success case : 한 서버의 수신자가 딱 500 명이면 한 번에 보낸다")
    void 한_서버의_수신자가_딱_500_명이면_한_번에_보낸다() {
        // arrange
        List<Long> members = memberIds(500);
        givenLocationsOnServer1(members);

        // act
        chatMessageDeliveryService.deliver("message-1", chatMessage, List.of(), chatMessageDeliveryService.locate(members));

        // assert
        assertThat(deliveredChunkSizes(CHAT_SERVER_1)).containsExactly(500);
    }

    @Test
    @DisplayName("success case : 한 서버에 501 명이면 500 / 1 로 나눠 보낸다")
    void 한_서버에_501_명이면_500_1_로_나눠_보낸다() {
        // arrange
        List<Long> members = memberIds(501);
        givenLocationsOnServer1(members);

        // act
        chatMessageDeliveryService.deliver("message-1", chatMessage, List.of(), chatMessageDeliveryService.locate(members));

        // assert
        assertThat(deliveredChunkSizes(CHAT_SERVER_1)).containsExactly(500, 1);
    }

    @Test
    @DisplayName("success case : 한 서버에 1200 명이면 500 / 500 / 200 으로 나눠 보낸다")
    void 한_서버에_1200_명이면_500_500_200_으로_나눠_보낸다() {
        // arrange
        List<Long> members = memberIds(1200);
        givenLocationsOnServer1(members);

        // act
        chatMessageDeliveryService.deliver("message-1", chatMessage, List.of(), chatMessageDeliveryService.locate(members));

        // assert
        assertThat(deliveredChunkSizes(CHAT_SERVER_1)).containsExactly(500, 500, 200);
    }

    @Test
    @DisplayName("success case : 멤버가 500 명을 넘으면 접속 위치를 500 개씩 MGET 해서 파이프라인 한 번으로 읽는다")
    void 멤버가_500_명을_넘으면_접속_위치를_500_개씩_MGET_해서_파이프라인_한_번으로_읽는다() {
        // arrange : 1200 명 = MGET 500 / 500 / 200
        List<Long> members = memberIds(1200);
        when(redisTemplate.getKeySerializer()).thenReturn((org.springframework.data.redis.serializer.RedisSerializer) StringRedisSerializer.UTF_8);
        ArgumentCaptor<RedisCallback<Object>> callback = ArgumentCaptor.forClass(RedisCallback.class);
        when(redisTemplate.executePipelined(callback.capture())).thenReturn(List.of(
            Collections.nCopies(500, at(SERVER_1)), Collections.nCopies(500, at(SERVER_2)), Collections.nCopies(200, null)));

        // act
        chatMessageDeliveryService.deliver("message-1", chatMessage, List.of(), chatMessageDeliveryService.locate(members));

        // assert : 파이프라인 안에서 MGET 을 500 / 500 / 200 키로 부른다
        RedisConnection connection = mock(RedisConnection.class);
        RedisStringCommands stringCommands = mock(RedisStringCommands.class);
        when(connection.stringCommands()).thenReturn(stringCommands);
        callback.getValue().doInRedis(connection);
        ArgumentCaptor<byte[][]> keys = ArgumentCaptor.forClass(byte[][].class);
        verify(stringCommands, times(3)).mGet(keys.capture());
        assertThat(keys.getAllValues()).extracting(batch -> batch.length).containsExactly(500, 500, 200);
        assertThat(new String(keys.getAllValues().get(0)[0], StandardCharsets.UTF_8)).isEqualTo("1");
        verify(valueOperations, never()).multiGet(any());
        // 앞 500 명은 서버 1, 다음 500 명은 서버 2, 마지막 200 명은 오프라인
        assertThat(deliveredChunkSizes(CHAT_SERVER_1)).containsExactly(500);
        assertThat(deliveredChunkSizes(CHAT_SERVER_2)).containsExactly(500);
    }

    @Test
    @DisplayName("success case : 서로 다른 서버에는 동시에 보내고 모두 끝난 뒤에 돌아온다")
    void 서로_다른_서버에는_동시에_보내고_모두_끝난_뒤에_돌아온다() throws Exception {
        // arrange : 서버 1 전송은 서버 2 전송이 시작될 때까지 기다린다 (차례로 보내면 여기서 막힌다)
        givenLocations(List.of("5", "9"), at(SERVER_1), at(SERVER_2));
        CountDownLatch server2Started = new CountDownLatch(1);
        AtomicBoolean server1Finished = new AtomicBoolean();
        doAnswer(invocation -> {
            assertThat(server2Started.await(5, TimeUnit.SECONDS)).isTrue();
            Thread.sleep(200);
            server1Finished.set(true);
            return null;
        }).when(chatServerClient).deliver(eq(CHAT_SERVER_1), any());
        doAnswer(invocation -> {
            server2Started.countDown();
            return null;
        }).when(chatServerClient).deliver(eq(CHAT_SERVER_2), any());

        // act
        chatMessageDeliveryService.deliver("message-1", chatMessage, List.of(), chatMessageDeliveryService.locate(List.of(5L, 9L)));

        // assert
        assertThat(server1Finished).isTrue();
    }

    @Test
    @DisplayName("fail case : 한 서버 전달이 실패해도 다른 서버에는 보내고, 끝에서 실패한 서버를 담아 예외를 던진다 (재시도 토픽으로)")
    void 한_서버_전달이_실패해도_다른_서버에는_보내고_끝에서_예외를_던진다() {
        // arrange
        givenLocations(List.of("5", "9"), at(SERVER_1), at(SERVER_2));
        lenient().doThrow(new RuntimeException("timeout")).when(chatServerClient).deliver(eq(CHAT_SERVER_1), any());

        // act
        DeliveryFailedException exception = catchThrowableOfType(
            () -> chatMessageDeliveryService.deliver("message-1", chatMessage, List.of(), chatMessageDeliveryService.locate(List.of(5L, 9L))), DeliveryFailedException.class);

        // assert
        assertDelivered(CHAT_SERVER_2, List.of(List.of(9L)));
        assertThat(exception.getFailedChatServers()).containsExactly(SERVER_1);
    }

    @Test
    @DisplayName("success case : 접속한 멤버가 없으면 아무 데도 보내지 않는다")
    void 접속한_멤버가_없으면_아무_데도_보내지_않는다() {
        // arrange
        givenLocations(List.of("5", "9"), null, null);

        // act
        chatMessageDeliveryService.deliver("message-1", chatMessage, List.of(), chatMessageDeliveryService.locate(List.of(5L, 9L)));

        // assert
        verify(chatServerClient, never()).deliver(any(), any());
    }

    @Test
    @DisplayName("success case : 확인된 첨부를 메시지와 함께 전달한다")
    void 확인된_첨부를_메시지와_함께_전달한다() {
        // arrange
        givenLocations(List.of("5"), at(SERVER_1));

        // act
        chatMessageDeliveryService.deliver("message-1", chatMessage,
            List.of(new Attachment("6acc66675f99715e286f0f6e", "사진.png", "image/png", 10L)), chatMessageDeliveryService.locate(List.of(5L)));

        // assert
        ArgumentCaptor<ChatMessageDelivery> captor = ArgumentCaptor.forClass(ChatMessageDelivery.class);
        verify(chatServerClient).deliver(eq(CHAT_SERVER_1), captor.capture());
        assertThat(captor.getValue()).isEqualTo(new ChatMessageDelivery("message-1", chatMessage, List.of(5L),
            List.of(new ChatMessageDelivery.Attachment("6acc66675f99715e286f0f6e", "사진.png", "image/png", 10L))));
    }

    @Test
    @DisplayName("success case : 이름 / 형식이 없는 첨부는 빈 문자열, 누른 사람 목록이 없는 리액션은 빈 목록으로 보낸다 (null 을 보내지 않는다)")
    void 이름_형식이_없는_첨부는_빈_문자열_누른_사람_목록이_없는_리액션은_빈_목록으로_보낸다() {
        // arrange
        givenLocations(List.of("5"), at(SERVER_1));

        // act
        ChatReceivers receivers = chatMessageDeliveryService.locate(List.of(5L));
        chatMessageDeliveryService.deliver("message-1", chatMessage,
            List.of(new Attachment("6acc66675f99715e286f0f6e", null, null, 10L)), receivers);
        chatMessageDeliveryService.deliverReaction(7L, "message-1", List.of(new Reaction("👍", null)), List.of(5L));

        // assert
        ArgumentCaptor<ChatMessageDelivery> message = ArgumentCaptor.forClass(ChatMessageDelivery.class);
        verify(chatServerClient).deliver(eq(CHAT_SERVER_1), message.capture());
        assertThat(message.getValue().attachments()).containsExactly(new ChatMessageDelivery.Attachment("6acc66675f99715e286f0f6e", "", "", 10L));
        ArgumentCaptor<ChatReactionDelivery> reaction = ArgumentCaptor.forClass(ChatReactionDelivery.class);
        verify(chatServerClient).deliverReaction(eq(CHAT_SERVER_1), reaction.capture());
        assertThat(reaction.getValue().reactions()).containsExactly(new ChatReactionDelivery.Reaction("👍", List.of()));
    }

    @Test
    @DisplayName("success case : 리액션은 같은 방법으로 고른 수신자에게 reactions 전체를 보내고, 실패하면 예외를 던진다")
    void 리액션은_같은_방법으로_고른_수신자에게_reactions_전체를_보내고_실패하면_예외를_던진다() {
        // arrange
        givenLocations(List.of("5", "17", "9"), at(SERVER_1), at(SERVER_1), at(SERVER_2));
        // lenient : 다른 서버(1)로의 호출이 같은 스텁과 인자가 달라도 실패로 치지 않게
        lenient().doThrow(new RuntimeException("timeout")).when(chatServerClient).deliverReaction(eq(CHAT_SERVER_2), any());

        // act
        DeliveryFailedException exception = catchThrowableOfType(() -> chatMessageDeliveryService.deliverReaction(7L, "message-1",
            List.of(new Reaction("👍", List.of(5L, 17L))), List.of(5L, 17L, 9L)), DeliveryFailedException.class);

        // assert
        ArgumentCaptor<ChatReactionDelivery> captor = ArgumentCaptor.forClass(ChatReactionDelivery.class);
        verify(chatServerClient).deliverReaction(eq(CHAT_SERVER_1), captor.capture());
        assertThat(captor.getValue().channelId()).isEqualTo(7L);
        assertThat(captor.getValue().messageId()).isEqualTo("message-1");
        assertThat(captor.getValue().receiveUserIds()).containsExactly(5L, 17L);
        assertThat(captor.getValue().reactions()).containsExactly(new ChatReactionDelivery.Reaction("👍", List.of(5L, 17L)));
        assertThat(exception.getFailedChatServers()).containsExactly(SERVER_2);
    }

    private void givenAliveChatServers(ServiceInstance... instances) {
        lenient().when(discoveryClient.getInstances("chatting")).thenReturn(List.of(instances));
    }

    /**
     * 읽는 쪽 RedisTemplate(JSON 직렬화)이 {userId} 의 새 값 {"server","status"} 를 읽으면 Map 이 온다
     */
    private static Map<String, String> at(String chatServer) {
        return Map.of("server", "ws://" + chatServer, "status", "online");
    }

    private void givenLocations(List<String> keys, Object... locations) {
        when(valueOperations.multiGet(keys)).thenReturn(new ArrayList<>(java.util.Arrays.asList(locations)));
    }

    /**
     * 모두 서버 1 에 접속. 500 명 이하는 MGET, 넘으면 파이프라인(500 개씩) 결과로 준다
     */
    @SuppressWarnings({"unchecked", "rawtypes"})
    private void givenLocationsOnServer1(List<Long> members) {
        if (members.size() <= ChatMessageDeliveryService.LOCATION_LOOKUP_CHUNK) {
            when(valueOperations.multiGet(members.stream().map(String::valueOf).toList()))
                .thenReturn(new ArrayList<>(Collections.nCopies(members.size(), at(SERVER_1))));
            return;
        }
        when(redisTemplate.getKeySerializer()).thenReturn((org.springframework.data.redis.serializer.RedisSerializer) StringRedisSerializer.UTF_8);
        List<Object> results = new ArrayList<>();
        for (List<Long> chunk : ChatMessageDeliveryService.chunks(members, ChatMessageDeliveryService.LOCATION_LOOKUP_CHUNK)) {
            results.add(Collections.nCopies(chunk.size(), at(SERVER_1)));
        }
        when(redisTemplate.executePipelined(any(RedisCallback.class))).thenReturn(results);
    }

    private static List<Long> memberIds(int count) {
        return LongStream.rangeClosed(1, count).boxed().toList();
    }

    private ServiceInstance chatServer(String host, int port) {
        return new DefaultServiceInstance(host + ":" + port, "chatting", host, port, false);
    }

    private List<Integer> deliveredChunkSizes(URI chatServerUri) {
        ArgumentCaptor<ChatMessageDelivery> captor = ArgumentCaptor.forClass(ChatMessageDelivery.class);
        verify(chatServerClient, org.mockito.Mockito.atLeastOnce()).deliver(eq(chatServerUri), captor.capture());
        return captor.getAllValues().stream().map(delivery -> delivery.receiveUserIds().size()).toList();
    }

    private void assertDelivered(URI chatServerUri, List<List<Long>> receiverChunks) {
        ArgumentCaptor<ChatMessageDelivery> captor = ArgumentCaptor.forClass(ChatMessageDelivery.class);
        verify(chatServerClient, times(receiverChunks.size())).deliver(eq(chatServerUri), captor.capture());
        assertThat(captor.getAllValues()).extracting(ChatMessageDelivery::receiveUserIds)
            .containsExactlyElementsOf(receiverChunks);
    }
}
