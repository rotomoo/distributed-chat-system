package com.distributed.chat.system.chatting.base.handler;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.distributed.chat.system.chatting.web.controller.feign.ClientApiClient;
import com.distributed.chat.system.chatting.web.controller.feign.KafkaProducerClient;
import com.distributed.chat.system.chatting.base.metrics.ChatServerMetrics;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.net.InetSocketAddress;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.SetOperations;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import protobuf.EdaChatMessage.ChatMessage;
import protobuf.EdaChatMessage.Attachment;
import protobuf.EdaChatMessage.ChatMessageDelivery;
import protobuf.EdaChatMessage.ChatReaction;
import protobuf.EdaChatMessage.ChatReactionDelivery;
import protobuf.EdaChatMessage.Reaction;

@DisplayName("[단위] CustomTextWebSocketHandler 테스트")
@ExtendWith(MockitoExtension.class)
class CustomTextWebSocketHandlerUnitTest {

    private static final String USER_ID = "5";

    @Mock
    private RedisTemplate<String, Object> redisTemplate;

    @Mock
    private ValueOperations<String, Object> valueOperations;

    @Mock
    private SetOperations<String, Object> setOperations;

    @Mock
    private KafkaProducerClient kafkaProducerClient;

    @Mock
    private ClientApiClient clientApiClient;

    private CustomTextWebSocketHandler handler;

    private final AtomicInteger sessionSequence = new AtomicInteger();

    private final MutableClock clock = new MutableClock(Instant.parse("2026-10-13T00:00:00Z"));

    private final SimpleMeterRegistry meterRegistry = new SimpleMeterRegistry();

    private ExecutorService executor;

    @BeforeEach
    void setup() {
        lenient().when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        lenient().when(redisTemplate.opsForSet()).thenReturn(setOperations);
        handler = new CustomTextWebSocketHandler(redisTemplate, kafkaProducerClient, clientApiClient, new ObjectMapper(),
            clock, new ChatServerMetrics(meterRegistry));
        executor = Executors.newFixedThreadPool(16);
    }

    @AfterEach
    void tearDown() {
        executor.shutdownNow();
    }

    @Test
    @DisplayName("success case : 같은 유저가 두 기기로 접속하면 둘 다 메시지를 받는다")
    void 같은_유저가_두_기기로_접속하면_둘_다_메시지를_받는다() throws Exception {
        // arrange
        WebSocketSession pc = session(USER_ID);
        WebSocketSession phone = session(USER_ID);
        handler.afterConnectionEstablished(pc);
        handler.afterConnectionEstablished(phone);

        // act
        handler.deliver(delivery("hello", 5L));

        // assert
        verify(pc).sendMessage(chatMessage());
        verify(phone).sendMessage(chatMessage());
        assertThat(handler.getSessions(USER_ID)).hasSize(2);
    }

    @Test
    @DisplayName("success case : 한 기기가 끊겨도 다른 기기는 계속 받고 Redis 접속 정보는 유지된다")
    void 한_기기가_끊겨도_다른_기기는_계속_받고_Redis_접속_정보는_유지된다() throws Exception {
        // arrange
        WebSocketSession pc = session(USER_ID);
        WebSocketSession phone = session(USER_ID);
        handler.afterConnectionEstablished(pc);
        handler.afterConnectionEstablished(phone);

        // act
        handler.afterConnectionClosed(phone, CloseStatus.NORMAL);
        handler.deliver(delivery("hello", 5L));

        // assert
        verify(pc).sendMessage(chatMessage());
        verify(phone, never()).sendMessage(chatMessage());
        verify(setOperations, never()).remove(anyString(), eq(USER_ID));
    }

    @Test
    @DisplayName("success case : 마지막 기기가 끊기면 Redis 접속 정보에서 한 번만 지운다")
    void 마지막_기기가_끊기면_Redis_접속_정보에서_한_번만_지운다() throws Exception {
        // arrange
        WebSocketSession pc = session(USER_ID);
        WebSocketSession phone = session(USER_ID);
        handler.afterConnectionEstablished(pc);
        handler.afterConnectionEstablished(phone);

        // act
        handler.afterConnectionClosed(phone, CloseStatus.NORMAL);
        handler.afterConnectionClosed(pc, CloseStatus.NORMAL);

        // assert
        verify(setOperations, times(1)).remove(anyString(), eq(USER_ID));
        assertThat(handler.getSessions(USER_ID)).isEmpty();
    }

    @Test
    @DisplayName("success case : 닫힌 소켓과 다른 유저에게는 보내지 않는다")
    void 닫힌_소켓과_다른_유저에게는_보내지_않는다() throws Exception {
        // arrange
        WebSocketSession closed = session(USER_ID);
        WebSocketSession otherUser = session("9");
        handler.afterConnectionEstablished(closed);
        handler.afterConnectionEstablished(otherUser);
        when(closed.isOpen()).thenReturn(false);

        // act
        handler.deliver(delivery("hello", 5L));

        // assert
        verify(closed, never()).sendMessage(chatMessage());
        verify(otherUser, never()).sendMessage(chatMessage());
    }

    @Test
    @DisplayName("success case : 100개 기기가 동시에 접속하고 동시에 끊겨도 Redis 에서는 마지막에 한 번만 지운다")
    void 기기_100개가_동시에_접속하고_끊겨도_Redis_에서는_마지막에_한_번만_지운다() throws Exception {
        // arrange
        List<WebSocketSession> devices = new ArrayList<>();
        for (int i = 0; i < 100; i++) {
            devices.add(session(USER_ID));
        }

        // act
        runConcurrently(devices, device -> handler.afterConnectionEstablished(device));
        int connectedCount = handler.getSessions(USER_ID).size();
        runConcurrently(devices, device -> handler.afterConnectionClosed(device, CloseStatus.NORMAL));

        // assert
        assertThat(connectedCount).isEqualTo(100);
        assertThat(handler.getSessions(USER_ID)).isEmpty();
        verify(setOperations, times(1)).remove(anyString(), eq(USER_ID));
    }

    @Test
    @DisplayName("success case : 마지막 기기 끊김과 새 기기 접속이 경쟁해도 Redis 접속 정보와 서버 메모리가 일치한다")
    void 마지막_기기_끊김과_새_기기_접속이_경쟁해도_Redis_접속_정보와_서버_메모리가_일치한다() throws Exception {
        // arrange : Redis Set 을 실제 Set 처럼 흉내 낸다 (SADD / SREM 결과를 기록)
        //           SREM 에 1ms 지연을 줘서 "메모리에서는 뺐는데 Redis 는 아직 안 지운" 틈을 넓힌다 (실제 네트워크 왕복)
        Set<Object> redisMembers = ConcurrentHashMap.newKeySet();
        doAnswer(invocation -> redisMembers.add(invocation.getArgument(1)) ? 1L : 0L)
            .when(setOperations).add(anyString(), any());
        doAnswer(invocation -> {
            Thread.sleep(1);
            return redisMembers.remove(invocation.getArgument(1)) ? 1L : 0L;
        }).when(setOperations).remove(anyString(), any());

        WebSocketSession current = session(USER_ID);
        handler.afterConnectionEstablished(current);

        for (int round = 0; round < 300; round++) {
            WebSocketSession leaving = current;
            WebSocketSession joining = session(USER_ID);

            // act : 지금 붙어 있는 마지막 기기가 끊기는 순간, 같은 유저의 새 기기가 동시에 붙는다
            CountDownLatch start = new CountDownLatch(1);
            Future<?> close = executor.submit(() -> {
                start.await();
                handler.afterConnectionClosed(leaving, CloseStatus.NORMAL);
                return null;
            });
            Future<?> open = executor.submit(() -> {
                start.await();
                handler.afterConnectionEstablished(joining);
                return null;
            });
            start.countDown();
            close.get(5, TimeUnit.SECONDS);
            open.get(5, TimeUnit.SECONDS);
            current = joining;

            // assert : 새 기기가 붙어 있으므로 Redis 에도 반드시 남아 있어야 한다
            assertThat(handler.getSessions(USER_ID)).hasSize(1);
            assertThat(redisMembers).as("round %d", round).contains(USER_ID);
        }
    }

    @Test
    @DisplayName("success case : 접속 정보를 Redis 에 기록한 다음에 준비 완료 신호를 보낸다")
    void 접속_정보를_Redis_에_기록한_다음에_준비_완료_신호를_보낸다() throws Exception {
        // arrange
        WebSocketSession pc = session(USER_ID);

        // act
        handler.afterConnectionEstablished(pc);

        // assert : 클라이언트가 신호를 받은 시점에는 이미 전달 대상(Redis)에 들어가 있어야 한다
        InOrder order = inOrder(setOperations, pc);
        order.verify(setOperations).add(anyString(), eq(USER_ID));
        order.verify(pc).sendMessage(argThat(message ->
            message instanceof TextMessage text && text.getPayload().contains("\"type\":\"connected\"")));
    }

    @Test
    @DisplayName("success case : 채널 멤버가 보낸 메시지는 채널 ID 와 함께 발행한다")
    void 채널_멤버가_보낸_메시지는_채널_ID_와_함께_발행한다() throws Exception {
        // arrange
        WebSocketSession pc = session(USER_ID);
        handler.afterConnectionEstablished(pc);
        when(redisTemplate.hasKey("channel:7:members")).thenReturn(true);
        when(setOperations.isMember("channel:7:members", USER_ID)).thenReturn(true);

        // act
        handler.handleMessage(pc, new TextMessage("{\"type\":\"message\",\"channelId\":7,\"message\":\"안녕\"}"));

        // assert
        ArgumentCaptor<ChatMessage> published = ArgumentCaptor.forClass(ChatMessage.class);
        verify(kafkaProducerClient).publishChatSendMessage(published.capture());
        assertThat(published.getValue().getChannelId()).isEqualTo(7L);
        assertThat(published.getValue().getSendUserId()).isEqualTo(5L);
        assertThat(published.getValue().getMessage()).isEqualTo("안녕");
    }

    @Test
    @DisplayName("fail case : 채널 멤버가 아니면 발행하지 않고 보낸 사람에게 오류를 보낸다")
    void 채널_멤버가_아니면_발행하지_않고_보낸_사람에게_오류를_보낸다() throws Exception {
        // arrange
        WebSocketSession pc = session(USER_ID);
        handler.afterConnectionEstablished(pc);
        when(redisTemplate.hasKey("channel:7:members")).thenReturn(true);
        when(setOperations.isMember("channel:7:members", USER_ID)).thenReturn(false);

        // act
        handler.handleMessage(pc, new TextMessage("{\"type\":\"message\",\"channelId\":7,\"message\":\"안녕\"}"));

        // assert
        verifyNoInteractions(kafkaProducerClient);
        verify(pc).sendMessage(errorFrame(3003));
    }

    @Test
    @DisplayName("success case : 채널 멤버 캐시가 없으면 client-api 에서 다시 채운 뒤 확인한다")
    void 채널_멤버_캐시가_없으면_client_api_에서_다시_채운_뒤_확인한다() throws Exception {
        // arrange
        WebSocketSession pc = session(USER_ID);
        handler.afterConnectionEstablished(pc);
        when(redisTemplate.hasKey("channel:7:members")).thenReturn(false);
        when(clientApiClient.refreshChannelMembers(7L)).thenReturn(List.of(5L, 9L));

        // act
        handler.handleMessage(pc, new TextMessage("{\"type\":\"message\",\"channelId\":7,\"message\":\"안녕\"}"));

        // assert
        verify(clientApiClient).refreshChannelMembers(7L);
        verify(kafkaProducerClient).publishChatSendMessage(any(ChatMessage.class));
    }

    @ParameterizedTest
    @ValueSource(strings = {
        "그냥 텍스트",
        "{\"type\":\"message\",\"message\":\"채널 없음\"}",
        "{\"type\":\"message\",\"channelId\":7,\"message\":\"   \"}",
        "{\"type\":\"ping\",\"channelId\":7,\"message\":\"안녕\"}"
    })
    @DisplayName("fail case : 형식이 틀린 메시지는 발행하지 않고 오류를 보낸다")
    void 형식이_틀린_메시지는_발행하지_않고_오류를_보낸다(String payload) throws Exception {
        // arrange
        WebSocketSession pc = session(USER_ID);
        handler.afterConnectionEstablished(pc);

        // act
        handler.handleMessage(pc, new TextMessage(payload));

        // assert
        verifyNoInteractions(kafkaProducerClient);
        verify(pc).sendMessage(errorFrame(4002));
    }

    // ── 첨부파일 ────────────────────────────────────────────────

    @Test
    @DisplayName("success case : 첨부가 있으면 글이 없어도 fileId 를 (소문자, 중복 없이) 실어 발행한다")
    void 첨부가_있으면_글이_없어도_fileId_를_실어_발행한다() throws Exception {
        // arrange
        WebSocketSession pc = memberSession();

        // act
        handler.handleMessage(pc, new TextMessage("{\"type\":\"message\",\"channelId\":7,\"message\":\"\","
            + "\"attachments\":[{\"fileId\":\"6ACC66675F99715E286F0F6E\"},{\"fileId\":\"6acc66675f99715e286f0f6e\"},"
            + "{\"fileId\":\"000000000000000000000001\",\"name\":\"믿지 않는 이름\"}]}"));

        // assert
        ArgumentCaptor<ChatMessage> published = ArgumentCaptor.forClass(ChatMessage.class);
        verify(kafkaProducerClient).publishChatSendMessage(published.capture());
        assertThat(published.getValue().getMessage()).isEmpty();
        assertThat(published.getValue().getAttachmentFileIdsList())
            .containsExactly("6acc66675f99715e286f0f6e", "000000000000000000000001");
    }

    @Test
    @DisplayName("success case : 첨부는 10개까지 보낼 수 있다")
    void 첨부는_10개까지_보낼_수_있다() throws Exception {
        // arrange
        WebSocketSession pc = memberSession();

        // act
        handler.handleMessage(pc, new TextMessage(messageWithAttachments(10)));

        // assert
        ArgumentCaptor<ChatMessage> published = ArgumentCaptor.forClass(ChatMessage.class);
        verify(kafkaProducerClient).publishChatSendMessage(published.capture());
        assertThat(published.getValue().getAttachmentFileIdsList()).hasSize(10);
    }

    @ParameterizedTest
    @ValueSource(strings = {
        "{\"type\":\"message\",\"channelId\":7,\"message\":\"\",\"attachments\":[]}",
        "{\"type\":\"message\",\"channelId\":7,\"message\":\"안녕\",\"attachments\":[{\"fileId\":\"abc\"}]}",
        "{\"type\":\"message\",\"channelId\":7,\"message\":\"안녕\",\"attachments\":[{}]}",
        "{\"type\":\"message\",\"channelId\":7,\"message\":\"안녕\",\"attachments\":{\"fileId\":\"6acc66675f99715e286f0f6e\"}}"
    })
    @DisplayName("fail case : 첨부 형식이 틀리거나 글과 첨부가 둘 다 비면 발행하지 않고 4002 를 보낸다")
    void 첨부_형식이_틀리거나_글과_첨부가_둘_다_비면_발행하지_않고_4002_를_보낸다(String payload) throws Exception {
        // arrange
        WebSocketSession pc = session(USER_ID);
        handler.afterConnectionEstablished(pc);

        // act
        handler.handleMessage(pc, new TextMessage(payload));

        // assert
        verifyNoInteractions(kafkaProducerClient);
        verify(pc).sendMessage(errorFrame(4002));
    }

    @Test
    @DisplayName("fail case : 첨부가 10개를 넘으면 발행하지 않고 4002 를 보낸다")
    void 첨부가_10개를_넘으면_발행하지_않고_4002_를_보낸다() throws Exception {
        // arrange
        WebSocketSession pc = session(USER_ID);
        handler.afterConnectionEstablished(pc);

        // act
        handler.handleMessage(pc, new TextMessage(messageWithAttachments(11)));

        // assert
        verifyNoInteractions(kafkaProducerClient);
        verify(pc).sendMessage(errorFrame(4002));
    }

    @Test
    @DisplayName("success case : 전달하는 메시지에 컨슈머가 확인한 첨부를 담고 없으면 빈 배열이다")
    void 전달하는_메시지에_컨슈머가_확인한_첨부를_담고_없으면_빈_배열이다() throws Exception {
        // arrange
        WebSocketSession pc = session(USER_ID);
        handler.afterConnectionEstablished(pc);
        ChatMessageDelivery withAttachment = delivery("", 5L).toBuilder()
            .addAttachments(Attachment.newBuilder().setFileId("6acc66675f99715e286f0f6e").setName("사진.png")
                .setContentType("image/png").setSize(10L).build())
            .build();

        // act
        handler.deliver(withAttachment);
        handler.deliver(delivery("hello", 5L));

        // assert
        ArgumentCaptor<TextMessage> sent = ArgumentCaptor.forClass(TextMessage.class);
        verify(pc, times(3)).sendMessage(sent.capture());
        Map<?, ?> first = new ObjectMapper().readValue(sent.getAllValues().get(1).getPayload(), Map.class);
        Map<?, ?> second = new ObjectMapper().readValue(sent.getAllValues().get(2).getPayload(), Map.class);
        assertThat(first.get("attachments")).isEqualTo(List.of(Map.of(
            "fileId", "6acc66675f99715e286f0f6e", "name", "사진.png", "contentType", "image/png", "size", 10)));
        assertThat(second.get("attachments")).isEqualTo(List.of());
        assertThat(second.get("messageId")).isEqualTo("message-1");
    }

    // ── 리액션 ──────────────────────────────────────────────────

    @Test
    @DisplayName("success case : 채널 멤버의 리액션 add / remove 를 발행한다")
    void 채널_멤버의_리액션_add_remove_를_발행한다() throws Exception {
        // arrange
        WebSocketSession pc = memberSession();

        // act
        handler.handleMessage(pc, new TextMessage(reaction("6ACC66675F99715E286F0F6E", "👍🏽", "add")));
        handler.handleMessage(pc, new TextMessage(reaction("6acc66675f99715e286f0f6e", "👍🏽", "remove")));

        // assert
        ArgumentCaptor<ChatReaction> published = ArgumentCaptor.forClass(ChatReaction.class);
        verify(kafkaProducerClient, times(2)).publishChatReaction(published.capture());
        ChatReaction add = published.getAllValues().get(0);
        assertThat(add.getSendUserId()).isEqualTo(5L);
        assertThat(add.getChannelId()).isEqualTo(7L);
        assertThat(add.getMessageId()).isEqualTo("6acc66675f99715e286f0f6e");
        assertThat(add.getEmoji()).isEqualTo("👍🏽");
        assertThat(add.getAction()).isEqualTo(ChatReaction.Action.ADD);
        assertThat(add.getPublishDt()).isPositive();
        assertThat(published.getAllValues().get(1).getAction()).isEqualTo(ChatReaction.Action.REMOVE);
    }

    @Test
    @DisplayName("success case : 이모지는 공백 없이 32자(코드 포인트)까지 된다")
    void 이모지는_공백_없이_32자까지_된다() throws Exception {
        // arrange
        WebSocketSession pc = memberSession();

        // act
        handler.handleMessage(pc, new TextMessage(reaction("6acc66675f99715e286f0f6e", "😀".repeat(32), "add")));

        // assert
        verify(kafkaProducerClient).publishChatReaction(any(ChatReaction.class));
    }

    @ParameterizedTest
    @ValueSource(strings = {
        "{\"type\":\"reaction\",\"channelId\":7,\"messageId\":\"abc\",\"emoji\":\"👍\",\"action\":\"add\"}",
        "{\"type\":\"reaction\",\"channelId\":7,\"messageId\":\"6acc66675f99715e286f0f6e\",\"emoji\":\"\",\"action\":\"add\"}",
        "{\"type\":\"reaction\",\"channelId\":7,\"messageId\":\"6acc66675f99715e286f0f6e\",\"emoji\":\"👍 👍\",\"action\":\"add\"}",
        "{\"type\":\"reaction\",\"channelId\":7,\"messageId\":\"6acc66675f99715e286f0f6e\",\"emoji\":\"👍\",\"action\":\"toggle\"}",
        "{\"type\":\"reaction\",\"messageId\":\"6acc66675f99715e286f0f6e\",\"emoji\":\"👍\",\"action\":\"add\"}"
    })
    @DisplayName("fail case : 리액션 형식이 틀리면 발행하지 않고 4002 를 보낸다")
    void 리액션_형식이_틀리면_발행하지_않고_4002_를_보낸다(String payload) throws Exception {
        // arrange
        WebSocketSession pc = session(USER_ID);
        handler.afterConnectionEstablished(pc);

        // act
        handler.handleMessage(pc, new TextMessage(payload));

        // assert
        verifyNoInteractions(kafkaProducerClient);
        verify(pc).sendMessage(errorFrame(4002));
    }

    @Test
    @DisplayName("fail case : 이모지가 32자를 넘으면 발행하지 않고 4002 를 보낸다")
    void 이모지가_32자를_넘으면_발행하지_않고_4002_를_보낸다() throws Exception {
        // arrange
        WebSocketSession pc = session(USER_ID);
        handler.afterConnectionEstablished(pc);

        // act
        handler.handleMessage(pc, new TextMessage(reaction("6acc66675f99715e286f0f6e", "😀".repeat(33), "add")));

        // assert
        verifyNoInteractions(kafkaProducerClient);
        verify(pc).sendMessage(errorFrame(4002));
    }

    @Test
    @DisplayName("fail case : 채널 멤버가 아니면 리액션을 발행하지 않고 3003 을 보낸다")
    void 채널_멤버가_아니면_리액션을_발행하지_않고_3003_을_보낸다() throws Exception {
        // arrange
        WebSocketSession pc = session(USER_ID);
        handler.afterConnectionEstablished(pc);
        when(redisTemplate.hasKey("channel:7:members")).thenReturn(true);
        when(setOperations.isMember("channel:7:members", USER_ID)).thenReturn(false);

        // act
        handler.handleMessage(pc, new TextMessage(reaction("6acc66675f99715e286f0f6e", "👍", "add")));

        // assert
        verifyNoInteractions(kafkaProducerClient);
        verify(pc).sendMessage(errorFrame(3003));
    }

    @Test
    @DisplayName("success case : 바뀐 reactions 전체를 수신자의 모든 기기에 보내고 다른 유저에게는 보내지 않는다")
    void 바뀐_reactions_전체를_수신자의_모든_기기에_보내고_다른_유저에게는_보내지_않는다() throws Exception {
        // arrange
        WebSocketSession pc = session(USER_ID);
        WebSocketSession phone = session(USER_ID);
        WebSocketSession otherUser = session("9");
        handler.afterConnectionEstablished(pc);
        handler.afterConnectionEstablished(phone);
        handler.afterConnectionEstablished(otherUser);

        // act
        handler.deliverReaction(ChatReactionDelivery.newBuilder()
            .setMessageId("6acc66675f99715e286f0f6e").setChannelId(7L)
            .addReactions(Reaction.newBuilder().setEmoji("👍").addAllUserIds(List.of(5L, 9L)).build())
            .addReceiveUserIds(5L)
            .build());

        // assert
        verify(pc).sendMessage(argThat((TextMessage frame) -> frame.getPayload().contains("\"type\":\"reaction\"")));
        verify(phone).sendMessage(argThat((TextMessage frame) -> frame.getPayload().contains("\"type\":\"reaction\"")));
        verify(otherUser, never()).sendMessage(argThat((TextMessage frame) -> frame.getPayload().contains("\"type\":\"reaction\"")));
        ArgumentCaptor<TextMessage> sent = ArgumentCaptor.forClass(TextMessage.class);
        verify(pc, times(2)).sendMessage(sent.capture());
        Map<?, ?> frame = new ObjectMapper().readValue(sent.getAllValues().get(1).getPayload(), Map.class);
        assertThat(frame).isEqualTo(Map.of(
            "type", "reaction",
            "channelId", 7,
            "messageId", "6acc66675f99715e286f0f6e",
            "reactions", List.of(Map.of("emoji", "👍", "userIds", List.of(5, 9)))));
    }

    private WebSocketSession memberSession() throws Exception {
        WebSocketSession pc = session(USER_ID);
        handler.afterConnectionEstablished(pc);
        when(redisTemplate.hasKey("channel:7:members")).thenReturn(true);
        when(setOperations.isMember("channel:7:members", USER_ID)).thenReturn(true);
        return pc;
    }

    private static String reaction(String messageId, String emoji, String action) {
        return "{\"type\":\"reaction\",\"channelId\":7,\"messageId\":\"" + messageId + "\",\"emoji\":\"" + emoji
            + "\",\"action\":\"" + action + "\"}";
    }

    private static String messageWithAttachments(int count) {
        List<String> attachments = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            attachments.add("{\"fileId\":\"%024x\"}".formatted(i + 1));
        }
        return "{\"type\":\"message\",\"channelId\":7,\"message\":\"\",\"attachments\":[" + String.join(",", attachments) + "]}";
    }

    // ── 하트비트 / 소켓 정리 / 메트릭 ──────────────────────────────

    @Test
    @DisplayName("success case : 하트비트는 열린 소켓 전부에 ping 을 보낸다")
    void 하트비트는_열린_소켓_전부에_ping_을_보낸다() throws Exception {
        // arrange
        WebSocketSession pc = session(USER_ID);
        WebSocketSession phone = session("9");
        handler.afterConnectionEstablished(pc);
        handler.afterConnectionEstablished(phone);
        clock.advance(Duration.ofSeconds(25));

        // act
        handler.heartbeat(Duration.ofSeconds(60));

        // assert
        verify(pc).sendMessage(pingFrame());
        verify(phone).sendMessage(pingFrame());
        verify(pc, never()).close(any());
        assertThat(meterRegistry.get("chat.frames.sent").counter().count()).isEqualTo(2);
    }

    @Test
    @DisplayName("fail case : timeout 동안 아무 프레임도 없던 소켓은 ping 대신 SESSION_NOT_RELIABLE 로 닫는다")
    void timeout_동안_아무_프레임도_없던_소켓은_닫는다() throws Exception {
        // arrange
        WebSocketSession silent = session(USER_ID);
        handler.afterConnectionEstablished(silent);
        clock.advance(Duration.ofSeconds(61));

        // act
        handler.heartbeat(Duration.ofSeconds(60));

        // assert
        verify(silent).close(CloseStatus.SESSION_NOT_RELIABLE);
        verify(silent, never()).sendMessage(pingFrame());
        assertThat(meterRegistry.get("chat.heartbeat.timeouts").counter().count()).isEqualTo(1);
    }

    @Test
    @DisplayName("success case : pong 이나 다른 프레임을 받으면 마지막 수신 시각이 갱신되어 닫히지 않는다")
    void pong_이나_다른_프레임을_받으면_마지막_수신_시각이_갱신되어_닫히지_않는다() throws Exception {
        // arrange
        WebSocketSession pongClient = session(USER_ID);
        WebSocketSession chattyClient = session("9");
        handler.afterConnectionEstablished(pongClient);
        handler.afterConnectionEstablished(chattyClient);
        clock.advance(Duration.ofSeconds(50));
        handler.handleMessage(pongClient, new TextMessage("{\"type\":\"pong\"}"));
        handler.handleMessage(chattyClient, new TextMessage("{\"type\":\"아무거나\"}"));
        clock.advance(Duration.ofSeconds(20));

        // act : 접속한 지 70초, 마지막 프레임은 20초 전
        handler.heartbeat(Duration.ofSeconds(60));

        // assert
        verify(pongClient, never()).close(any());
        verify(chattyClient, never()).close(any());
        verify(pongClient).sendMessage(pingFrame());
        verify(pongClient, never()).sendMessage(errorFrame(4002));
    }

    @Test
    @DisplayName("fail case : 보내다 실패한 소켓은 SESSION_NOT_RELIABLE 로 닫고 다른 기기에는 계속 보낸다")
    void 보내다_실패한_소켓은_닫고_다른_기기에는_계속_보낸다() throws Exception {
        // arrange
        WebSocketSession broken = session(USER_ID);
        WebSocketSession pc = session(USER_ID);
        handler.afterConnectionEstablished(broken);
        handler.afterConnectionEstablished(pc);
        doThrow(new java.io.IOException("broken pipe")).when(broken).sendMessage(chatMessage());

        // act
        handler.deliver(delivery("hello", 5L));

        // assert
        verify(broken).close(CloseStatus.SESSION_NOT_RELIABLE);
        verify(pc).sendMessage(chatMessage());
        assertThat(meterRegistry.get("chat.send.failures").counter().count()).isEqualTo(1);
    }

    @Test
    @DisplayName("success case : 닫힌 소켓은 하트비트 대상에서 빠지고 열린 소켓 수 게이지가 줄어든다")
    void 닫힌_소켓은_하트비트_대상에서_빠지고_열린_소켓_수_게이지가_줄어든다() throws Exception {
        // arrange
        WebSocketSession pc = session(USER_ID);
        WebSocketSession phone = session(USER_ID);
        handler.afterConnectionEstablished(pc);
        handler.afterConnectionEstablished(phone);
        double before = meterRegistry.get("chat.websocket.sessions").gauge().value();

        // act
        handler.afterConnectionClosed(phone, CloseStatus.SESSION_NOT_RELIABLE);
        clock.advance(Duration.ofSeconds(25));
        handler.heartbeat(Duration.ofSeconds(60));

        // assert
        assertThat(before).isEqualTo(2);
        assertThat(meterRegistry.get("chat.websocket.sessions").gauge().value()).isEqualTo(1);
        verify(phone, never()).sendMessage(pingFrame());
        verify(pc).sendMessage(pingFrame());
    }

    @Test
    @DisplayName("success case : 발행하는 메시지마다 새 eventId 를 붙이고 받은 / 발행한 수를 센다")
    void 발행하는_메시지마다_새_eventId_를_붙이고_받은_발행한_수를_센다() throws Exception {
        // arrange
        WebSocketSession pc = memberSession();

        // act
        handler.handleMessage(pc, new TextMessage("{\"type\":\"message\",\"channelId\":7,\"message\":\"하나\"}"));
        handler.handleMessage(pc, new TextMessage("{\"type\":\"message\",\"channelId\":7,\"message\":\"둘\"}"));
        handler.handleMessage(pc, new TextMessage("{\"type\":\"message\",\"channelId\":0,\"message\":\"형식 오류\"}"));

        // assert
        ArgumentCaptor<ChatMessage> published = ArgumentCaptor.forClass(ChatMessage.class);
        verify(kafkaProducerClient, times(2)).publishChatSendMessage(published.capture());
        String first = published.getAllValues().get(0).getEventId();
        String second = published.getAllValues().get(1).getEventId();
        assertThat(java.util.UUID.fromString(first)).isNotNull();
        assertThat(first).isNotEqualTo(second);
        assertThat(meterRegistry.get("chat.messages.received").tag("type", "message").counter().count()).isEqualTo(3);
        assertThat(meterRegistry.get("chat.messages.published").tag("type", "message").counter().count()).isEqualTo(2);
    }

    private static TextMessage pingFrame() {
        return argThat((TextMessage message) -> message != null && message.getPayload().equals("{\"type\":\"ping\"}"));
    }

    /**
     * 테스트에서 시간을 앞으로 돌리는 시계
     */
    private static final class MutableClock extends Clock {

        private Instant now;

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

    private static TextMessage errorFrame(int code) {
        return argThat((TextMessage message) -> message.getPayload().contains("\"type\":\"error\"")
            && message.getPayload().contains("\"code\":" + code));
    }

    private static TextMessage chatMessage() {
        return argThat((TextMessage message) -> message.getPayload().contains("\"type\":\"message\""));
    }

    private WebSocketSession session(String userId) {
        WebSocketSession session = mock(WebSocketSession.class);
        Map<String, Object> attributes = new HashMap<>();
        attributes.put("userId", userId);
        lenient().when(session.getId()).thenReturn("session-" + sessionSequence.incrementAndGet());
        lenient().when(session.getAttributes()).thenReturn(attributes);
        lenient().when(session.getLocalAddress()).thenReturn(new InetSocketAddress(41329));
        lenient().when(session.isOpen()).thenReturn(true);
        return session;
    }

    private ChatMessageDelivery delivery(String message, Long... receiveUserIds) {
        return ChatMessageDelivery.newBuilder()
            .setMessageId("message-1")
            .setChatMessage(ChatMessage.newBuilder()
                .setSendUserId(9L)
                .setChannelId(0L)
                .setMessage(message)
                .setPublishDt(System.currentTimeMillis())
                .build())
            .addAllReceiveUserIds(List.of(receiveUserIds))
            .build();
    }

    private interface SessionTask {

        void run(WebSocketSession session) throws Exception;
    }

    private void runConcurrently(List<WebSocketSession> sessions, SessionTask task) throws Exception {
        CountDownLatch start = new CountDownLatch(1);
        List<Future<Void>> futures = new ArrayList<>();
        for (WebSocketSession session : sessions) {
            Callable<Void> callable = () -> {
                start.await();
                task.run(session);
                return null;
            };
            futures.add(executor.submit(callable));
        }
        start.countDown();
        for (Future<Void> future : futures) {
            future.get(10, TimeUnit.SECONDS);
        }
    }
}
