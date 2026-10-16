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

import com.distributed.chat.system.chatting.base.presence.UserLocationRegistry;
import com.distributed.chat.system.chatting.web.controller.feign.KafkaProducerClient;
import com.distributed.chat.system.common.event.ChatMessageDelivery;
import com.distributed.chat.system.common.event.ChatMessageDelivery.Attachment;
import com.distributed.chat.system.common.event.ChatMessageEvent;
import com.distributed.chat.system.common.event.ChatReactionDelivery;
import com.distributed.chat.system.common.event.ChatReactionDelivery.Reaction;
import com.distributed.chat.system.common.event.ChatReactionEvent;
import com.fasterxml.jackson.databind.ObjectMapper;
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
import java.util.concurrent.atomic.AtomicReference;
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
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;

@DisplayName("[단위] CustomTextWebSocketHandler 테스트")
@ExtendWith(MockitoExtension.class)
class CustomTextWebSocketHandlerUnitTest {

    private static final String USER_ID = "5";

    @Mock
    private KafkaProducerClient kafkaProducerClient;

    @Mock
    private UserLocationRegistry userLocationRegistry;

    private CustomTextWebSocketHandler handler;

    private final AtomicInteger sessionSequence = new AtomicInteger();

    private final MutableClock clock = new MutableClock(Instant.parse("2026-10-13T00:00:00Z"));


    private ExecutorService executor;

    @BeforeEach
    void setup() {
        // 기본 : 이 서버로 접속 위치를 잡는다 (다른 살아 있는 서버가 갖고 있는 경우는 따로 테스트)
        lenient().when(userLocationRegistry.claim(anyString(), anyString())).thenReturn(true);
        lenient().when(userLocationRegistry.writePresence(anyString(), anyString(), anyString())).thenReturn(true);
        handler = new CustomTextWebSocketHandler(kafkaProducerClient, userLocationRegistry, new ObjectMapper(), clock);
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
        verify(userLocationRegistry, never()).release(anyString(), anyString());
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
        verify(userLocationRegistry, times(1)).release(eq(USER_ID), anyString());
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
        verify(userLocationRegistry, times(1)).release(eq(USER_ID), anyString());
    }

    @Test
    @DisplayName("success case : 마지막 기기 끊김과 새 기기 접속이 경쟁해도 Redis 접속 정보와 서버 메모리가 일치한다")
    void 마지막_기기_끊김과_새_기기_접속이_경쟁해도_Redis_접속_정보와_서버_메모리가_일치한다() throws Exception {
        // arrange : Redis {userId} 대신 AtomicReference 하나를 쓴다 (claim = 이 서버로 SET, release = 이 서버일 때만 DEL)
        //           release 에 1ms 지연을 줘서 "메모리에서는 뺐는데 Redis 는 아직 안 지운" 틈을 넓힌다 (실제 네트워크 왕복)
        AtomicReference<String> location = new AtomicReference<>();
        doAnswer(invocation -> {
            location.set(invocation.getArgument(1));
            return true;
        }).when(userLocationRegistry).claim(anyString(), anyString());
        doAnswer(invocation -> {
            Thread.sleep(1);
            location.compareAndSet(invocation.getArgument(1), null);
            return null;
        }).when(userLocationRegistry).release(anyString(), anyString());

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
            assertThat(location.get()).as("round %d", round).isNotNull();
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
        InOrder order = inOrder(userLocationRegistry, pc);
        order.verify(userLocationRegistry).claim(eq(USER_ID), anyString());
        order.verify(pc).sendMessage(argThat(message ->
            message instanceof TextMessage text && text.getPayload().contains("\"type\":\"connected\"")));
    }

    @Test
    @DisplayName("success case : 형식이 맞는 메시지는 채널 ID 와 함께 발행한다 (채널 멤버 확인은 컨슈머가 한다)")
    void 형식이_맞는_메시지는_채널_ID_와_함께_발행한다() throws Exception {
        // arrange
        WebSocketSession pc = memberSession();

        // act
        handler.handleMessage(pc, new TextMessage("{\"type\":\"message\",\"channelId\":7,\"message\":\"안녕\"}"));

        // assert
        ArgumentCaptor<ChatMessageEvent> published = ArgumentCaptor.forClass(ChatMessageEvent.class);
        verify(kafkaProducerClient).publishChatSendMessage(published.capture());
        assertThat(published.getValue().channelId()).isEqualTo(7L);
        assertThat(published.getValue().sendUserId()).isEqualTo(5L);
        assertThat(published.getValue().message()).isEqualTo("안녕");
        verify(pc, never()).sendMessage(errorFrame(3003));
    }

    // ── 접속 위치 (유저 하나 = 서버 하나) ───────────────────────────

    @Test
    @DisplayName("fail case : 이 유저가 살아 있는 다른 서버에 있으면 4409 로 닫고 메모리에 넣지도 준비 완료 신호를 보내지도 않는다")
    void 이_유저가_살아_있는_다른_서버에_있으면_4409_로_닫는다() throws Exception {
        // arrange
        WebSocketSession pc = session(USER_ID);
        when(userLocationRegistry.claim(eq(USER_ID), anyString())).thenReturn(false);

        // act
        handler.afterConnectionEstablished(pc);
        handler.afterConnectionClosed(pc, CustomTextWebSocketHandler.CONNECTED_TO_OTHER_SERVER);

        // assert
        verify(pc).close(new CloseStatus(4409, "connected to another chat server"));
        verify(pc, never()).sendMessage(any());
        assertThat(handler.getSessions(USER_ID)).isEmpty();
        verify(userLocationRegistry, never()).release(anyString(), anyString());
    }

    @Test
    @DisplayName("success case : 기기마다 이 서버를 접속 위치로 잡고 마지막 기기가 끊길 때만 이 서버 값으로 지운다")
    void 기기마다_이_서버를_접속_위치로_잡고_마지막_기기가_끊길_때만_이_서버_값으로_지운다() throws Exception {
        // arrange
        WebSocketSession pc = session(USER_ID);
        WebSocketSession phone = session(USER_ID);
        handler.afterConnectionEstablished(pc);
        handler.afterConnectionEstablished(phone);

        // act
        handler.afterConnectionClosed(pc, CloseStatus.NORMAL);
        handler.afterConnectionClosed(phone, CloseStatus.NORMAL);

        // assert
        ArgumentCaptor<String> claimed = ArgumentCaptor.forClass(String.class);
        verify(userLocationRegistry, times(2)).claim(eq(USER_ID), claimed.capture());
        verify(userLocationRegistry, times(1)).release(USER_ID, claimed.getValue());
        assertThat(claimed.getValue()).endsWith(":41329");
    }

    @ParameterizedTest
    @ValueSource(strings = {
        "그냥 텍스트",
        "{\"type\":\"message\",\"message\":\"채널 없음\"}",
        "{\"type\":\"message\",\"channelId\":7,\"message\":\"   \"}",
        "{\"type\":\"ping\",\"channelId\":7,\"message\":\"안녕\"}",
        "{\"type\":\"presence\",\"state\":\"busy\"}",
        "{\"type\":\"presence\"}"
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

    // ── 접속 상태 (online / away) ──────────────────────────────

    @Test
    @DisplayName("success case : 처음 붙으면 접속 위치를 잡은 뒤 online 을 쓰고 준비 완료 신호를 보낸다")
    void 처음_붙으면_접속_위치를_잡은_뒤_online_을_쓰고_준비_완료_신호를_보낸다() throws Exception {
        // arrange
        WebSocketSession pc = session(USER_ID);

        // act
        handler.afterConnectionEstablished(pc);

        // assert
        String chatServer = chatServerOf(pc);
        InOrder order = inOrder(userLocationRegistry, pc);
        order.verify(userLocationRegistry).claim(USER_ID, chatServer);
        order.verify(userLocationRegistry).writePresence(USER_ID, chatServer, "online");
        order.verify(pc).sendMessage(argThat(message ->
            message instanceof TextMessage text && text.getPayload().contains("\"type\":\"connected\"")));
    }

    @Test
    @DisplayName("success case : 이미 online 인 유저의 두 번째 기기가 붙으면 다시 쓰지 않는다")
    void 이미_online_인_유저의_두_번째_기기가_붙으면_다시_쓰지_않는다() throws Exception {
        // arrange
        WebSocketSession pc = session(USER_ID);
        handler.afterConnectionEstablished(pc);

        // act
        handler.afterConnectionEstablished(session(USER_ID));

        // assert
        verify(userLocationRegistry, times(1)).writePresence(eq(USER_ID), anyString(), anyString());
    }

    @Test
    @DisplayName("fail case : 다른 살아 있는 서버에 있는 유저는 접속 상태를 쓰지 않는다")
    void 다른_살아_있는_서버에_있는_유저는_접속_상태를_쓰지_않는다() throws Exception {
        // arrange
        WebSocketSession pc = session(USER_ID);
        when(userLocationRegistry.claim(eq(USER_ID), anyString())).thenReturn(false);

        // act
        handler.afterConnectionEstablished(pc);
        clock.advance(Duration.ofSeconds(30));
        handler.handleMessage(pc, new TextMessage("{\"type\":\"presence\",\"state\":\"away\"}"));

        // assert
        verify(userLocationRegistry, never()).writePresence(anyString(), anyString(), anyString());
    }

    @Test
    @DisplayName("success case : 하나뿐인 기기가 away 를 보내면 away, 다시 active 를 보내면 online 을 쓴다")
    void 하나뿐인_기기가_away_를_보내면_away_다시_active_를_보내면_online_을_쓴다() throws Exception {
        // arrange
        WebSocketSession pc = memberSession();

        // act
        handler.handleMessage(pc, new TextMessage("{\"type\":\"presence\",\"state\":\"away\"}"));
        handler.handleMessage(pc, new TextMessage("{\"type\":\"presence\",\"state\":\"away\"}"));
        handler.handleMessage(pc, new TextMessage("{\"type\":\"presence\",\"state\":\"active\"}"));

        // assert : 접속 online → away → online (같은 상태를 두 번 받아도 한 번만 쓴다)
        InOrder order = inOrder(userLocationRegistry);
        order.verify(userLocationRegistry).writePresence(USER_ID, chatServerOf(pc), "online");
        order.verify(userLocationRegistry).writePresence(USER_ID, chatServerOf(pc), "away");
        order.verify(userLocationRegistry).writePresence(USER_ID, chatServerOf(pc), "online");
        verify(userLocationRegistry, times(3)).writePresence(anyString(), anyString(), anyString());
        verify(pc, never()).sendMessage(errorFrame(4002));
        verifyNoInteractions(kafkaProducerClient);
    }

    @Test
    @DisplayName("success case : 기기가 여러 개면 하나라도 active 면 online 을 유지하고 모두 away 일 때만 away 를 쓴다")
    void 기기가_여러_개면_하나라도_active_면_online_을_유지하고_모두_away_일_때만_away_를_쓴다() throws Exception {
        // arrange
        WebSocketSession pc = session(USER_ID);
        WebSocketSession phone = session(USER_ID);
        handler.afterConnectionEstablished(pc);
        handler.afterConnectionEstablished(phone);

        // act & assert : PC 만 away → 폰이 active 라 online 그대로
        handler.handleMessage(pc, new TextMessage("{\"type\":\"presence\",\"state\":\"away\"}"));
        verify(userLocationRegistry, never()).writePresence(anyString(), anyString(), eq("away"));

        // act & assert : 폰도 away → 모두 away
        handler.handleMessage(phone, new TextMessage("{\"type\":\"presence\",\"state\":\"away\"}"));
        verify(userLocationRegistry, times(1)).writePresence(USER_ID, chatServerOf(pc), "away");

        // act & assert : 폰이 다시 active → online
        handler.handleMessage(phone, new TextMessage("{\"type\":\"presence\",\"state\":\"active\"}"));
        verify(userLocationRegistry, times(2)).writePresence(USER_ID, chatServerOf(pc), "online");
    }

    @Test
    @DisplayName("success case : away 기기만 남기고 active 기기가 끊기면 away 로 바꾸고 위치는 지우지 않는다")
    void away_기기만_남기고_active_기기가_끊기면_away_로_바꾸고_위치는_지우지_않는다() throws Exception {
        // arrange
        WebSocketSession pc = session(USER_ID);
        WebSocketSession phone = session(USER_ID);
        handler.afterConnectionEstablished(pc);
        handler.afterConnectionEstablished(phone);
        handler.handleMessage(phone, new TextMessage("{\"type\":\"presence\",\"state\":\"away\"}"));

        // act
        handler.afterConnectionClosed(pc, CloseStatus.NORMAL);

        // assert
        verify(userLocationRegistry).writePresence(USER_ID, chatServerOf(pc), "away");
        verify(userLocationRegistry, never()).release(anyString(), anyString());
    }

    @Test
    @DisplayName("success case : away 기기가 끊겨서 active 기기만 남으면 online 은 그대로라 쓰지 않는다")
    void away_기기가_끊겨서_active_기기만_남으면_쓰지_않는다() throws Exception {
        // arrange
        WebSocketSession pc = session(USER_ID);
        WebSocketSession phone = session(USER_ID);
        handler.afterConnectionEstablished(pc);
        handler.afterConnectionEstablished(phone);
        handler.handleMessage(phone, new TextMessage("{\"type\":\"presence\",\"state\":\"away\"}"));

        // act
        handler.afterConnectionClosed(phone, CloseStatus.NORMAL);

        // assert : 접속할 때 쓴 online 1번뿐
        verify(userLocationRegistry, times(1)).writePresence(anyString(), anyString(), anyString());
    }

    @Test
    @DisplayName("success case : 마지막 기기가 끊기면 접속 상태를 쓰지 않고 위치와 상태를 함께 지우도록 release 만 부른다")
    void 마지막_기기가_끊기면_접속_상태를_쓰지_않고_release_만_부른다() throws Exception {
        // arrange
        WebSocketSession pc = memberSession();
        handler.handleMessage(pc, new TextMessage("{\"type\":\"presence\",\"state\":\"away\"}"));

        // act
        handler.afterConnectionClosed(pc, CloseStatus.NORMAL);

        // assert
        verify(userLocationRegistry, times(2)).writePresence(anyString(), anyString(), anyString());
        verify(userLocationRegistry).release(USER_ID, chatServerOf(pc));
    }

    @Test
    @DisplayName("success case : 받는 프레임마다 TTL 을 갱신하되 유저당 20초에 1번만 지금 상태로 다시 쓴다")
    void 받는_프레임마다_TTL_을_갱신하되_유저당_20초에_1번만_지금_상태로_다시_쓴다() throws Exception {
        // arrange
        WebSocketSession pc = session(USER_ID);
        WebSocketSession phone = session(USER_ID);
        handler.afterConnectionEstablished(pc);
        handler.afterConnectionEstablished(phone);

        // act & assert : 19.999초 → 아직
        clock.advance(Duration.ofMillis(19_999));
        handler.handleMessage(pc, new TextMessage("{\"type\":\"pong\"}"));
        handler.handleMessage(phone, new TextMessage("{\"type\":\"pong\"}"));
        verify(userLocationRegistry, times(1)).writePresence(anyString(), anyString(), anyString());

        // act & assert : 20초 → 한 기기의 프레임으로 1번 쓰고, 같은 순간 다른 기기 프레임은 건너뛴다
        clock.advance(Duration.ofMillis(1));
        handler.handleMessage(pc, new TextMessage("{\"type\":\"pong\"}"));
        handler.handleMessage(phone, new TextMessage("{\"type\":\"아무거나\"}"));
        verify(userLocationRegistry, times(2)).writePresence(USER_ID, chatServerOf(pc), "online");

        // act & assert : away 인 상태에서 20초 뒤에는 away 로 갱신
        handler.handleMessage(pc, new TextMessage("{\"type\":\"presence\",\"state\":\"away\"}"));
        handler.handleMessage(phone, new TextMessage("{\"type\":\"presence\",\"state\":\"away\"}"));
        clock.advance(Duration.ofSeconds(20));
        handler.handleMessage(phone, new TextMessage("{\"type\":\"pong\"}"));
        verify(userLocationRegistry, times(2)).writePresence(USER_ID, chatServerOf(pc), "away");
        verify(userLocationRegistry, times(4)).writePresence(anyString(), anyString(), anyString());
    }

    @Test
    @DisplayName("success case : 메시지 프레임도 TTL 갱신 대상이고 발행은 그대로 한다")
    void 메시지_프레임도_TTL_갱신_대상이고_발행은_그대로_한다() throws Exception {
        // arrange
        WebSocketSession pc = memberSession();
        clock.advance(Duration.ofSeconds(21));

        // act
        handler.handleMessage(pc, new TextMessage("{\"type\":\"message\",\"channelId\":7,\"message\":\"안녕\"}"));

        // assert
        verify(userLocationRegistry, times(2)).writePresence(USER_ID, chatServerOf(pc), "online");
        verify(kafkaProducerClient).publishChatSendMessage(any(ChatMessageEvent.class));
    }

    @Test
    @DisplayName("fail case : 접속 상태 쓰기가 실패해도 접속과 메시지 처리는 계속하고 다음 프레임에서 다시 쓴다")
    void 접속_상태_쓰기가_실패해도_접속과_메시지_처리는_계속하고_다음_프레임에서_다시_쓴다() throws Exception {
        // arrange
        when(userLocationRegistry.writePresence(anyString(), anyString(), anyString()))
            .thenThrow(new IllegalStateException("redis down"))
            .thenReturn(true);
        WebSocketSession pc = session(USER_ID);

        // act
        handler.afterConnectionEstablished(pc);
        handler.handleMessage(pc, new TextMessage("{\"type\":\"message\",\"channelId\":7,\"message\":\"안녕\"}"));

        // assert
        verify(pc).sendMessage(argThat(message ->
            message instanceof TextMessage text && text.getPayload().contains("\"type\":\"connected\"")));
        verify(kafkaProducerClient).publishChatSendMessage(any(ChatMessageEvent.class));
        verify(userLocationRegistry, times(2)).writePresence(USER_ID, chatServerOf(pc), "online");
        assertThat(handler.getSessions(USER_ID)).hasSize(1);
    }

    @Test
    @DisplayName("success case : 위치 키가 다른 서버로 넘어가 쓰지 못해도 20초 동안은 다시 시도하지 않는다")
    void 위치_키가_다른_서버로_넘어가_쓰지_못해도_20초_동안은_다시_시도하지_않는다() throws Exception {
        // arrange
        when(userLocationRegistry.writePresence(anyString(), anyString(), anyString())).thenReturn(false);
        WebSocketSession pc = memberSession();

        // act
        clock.advance(Duration.ofSeconds(10));
        handler.handleMessage(pc, new TextMessage("{\"type\":\"pong\"}"));

        // assert
        verify(userLocationRegistry, times(1)).writePresence(anyString(), anyString(), anyString());
    }

    @Test
    @DisplayName("success case : 여러 기기가 동시에 away / active 를 보내도 마지막에 쓴 상태가 메모리의 합친 상태와 같다")
    void 여러_기기가_동시에_away_active_를_보내도_마지막에_쓴_상태가_합친_상태와_같다() throws Exception {
        // arrange : Redis {userId} 의 status 대신 AtomicReference 하나를 쓴다
        AtomicReference<String> presence = new AtomicReference<>();
        doAnswer(invocation -> {
            Thread.sleep(1);
            presence.set(invocation.getArgument(2));
            return true;
        }).when(userLocationRegistry).writePresence(anyString(), anyString(), anyString());
        List<WebSocketSession> devices = new ArrayList<>();
        for (int i = 0; i < 20; i++) {
            devices.add(session(USER_ID));
        }
        for (WebSocketSession device : devices) {
            handler.afterConnectionEstablished(device);
        }

        // act : 한 기기만 active 로 끝나고 나머지는 away
        runConcurrently(devices, device -> {
            handler.handleMessage(device, new TextMessage("{\"type\":\"presence\",\"state\":\"away\"}"));
            handler.handleMessage(device, new TextMessage("{\"type\":\"presence\",\"state\":\"active\"}"));
            if (device != devices.get(0)) {
                handler.handleMessage(device, new TextMessage("{\"type\":\"presence\",\"state\":\"away\"}"));
            }
        });

        // assert
        assertThat(presence.get()).isEqualTo("online");

        // act : 마지막 active 기기도 away
        handler.handleMessage(devices.get(0), new TextMessage("{\"type\":\"presence\",\"state\":\"away\"}"));

        // assert
        assertThat(presence.get()).isEqualTo("away");
    }

    private static String chatServerOf(WebSocketSession session) {
        return (String) session.getAttributes().get("chatServerUri");
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
        ArgumentCaptor<ChatMessageEvent> published = ArgumentCaptor.forClass(ChatMessageEvent.class);
        verify(kafkaProducerClient).publishChatSendMessage(published.capture());
        assertThat(published.getValue().message()).isEmpty();
        assertThat(published.getValue().attachmentFileIds())
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
        ArgumentCaptor<ChatMessageEvent> published = ArgumentCaptor.forClass(ChatMessageEvent.class);
        verify(kafkaProducerClient).publishChatSendMessage(published.capture());
        assertThat(published.getValue().attachmentFileIds()).hasSize(10);
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
        ChatMessageDelivery withAttachment = new ChatMessageDelivery("message-1", delivery("", 5L).chatMessage(), List.of(5L),
            List.of(new Attachment("6acc66675f99715e286f0f6e", "사진.png", "image/png", 10L)));

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
    @DisplayName("success case : 형식이 맞는 리액션 add / remove 를 발행한다")
    void 형식이_맞는_리액션_add_remove_를_발행한다() throws Exception {
        // arrange
        WebSocketSession pc = memberSession();

        // act
        handler.handleMessage(pc, new TextMessage(reaction("6ACC66675F99715E286F0F6E", "👍🏽", "add")));
        handler.handleMessage(pc, new TextMessage(reaction("6acc66675f99715e286f0f6e", "👍🏽", "remove")));

        // assert
        ArgumentCaptor<ChatReactionEvent> published = ArgumentCaptor.forClass(ChatReactionEvent.class);
        verify(kafkaProducerClient, times(2)).publishChatReaction(published.capture());
        ChatReactionEvent add = published.getAllValues().get(0);
        assertThat(add.sendUserId()).isEqualTo(5L);
        assertThat(add.channelId()).isEqualTo(7L);
        assertThat(add.messageId()).isEqualTo("6acc66675f99715e286f0f6e");
        assertThat(add.emoji()).isEqualTo("👍🏽");
        assertThat(add.action()).isEqualTo(ChatReactionEvent.Action.ADD);
        assertThat(add.publishDt()).isPositive();
        assertThat(published.getAllValues().get(1).action()).isEqualTo(ChatReactionEvent.Action.REMOVE);
    }

    @Test
    @DisplayName("success case : 이모지는 공백 없이 32자(코드 포인트)까지 된다")
    void 이모지는_공백_없이_32자까지_된다() throws Exception {
        // arrange
        WebSocketSession pc = memberSession();

        // act
        handler.handleMessage(pc, new TextMessage(reaction("6acc66675f99715e286f0f6e", "😀".repeat(32), "add")));

        // assert
        verify(kafkaProducerClient).publishChatReaction(any(ChatReactionEvent.class));
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
        handler.deliverReaction(new ChatReactionDelivery("6acc66675f99715e286f0f6e", 7L,
            List.of(new Reaction("👍", List.of(5L, 9L))), List.of(5L)));

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
    }

    @Test
    @DisplayName("success case : 닫힌 소켓은 하트비트 대상에서 빠진다")
    void 닫힌_소켓은_하트비트_대상에서_빠진다() throws Exception {
        // arrange
        WebSocketSession pc = session(USER_ID);
        WebSocketSession phone = session(USER_ID);
        handler.afterConnectionEstablished(pc);
        handler.afterConnectionEstablished(phone);

        // act
        handler.afterConnectionClosed(phone, CloseStatus.SESSION_NOT_RELIABLE);
        clock.advance(Duration.ofSeconds(25));
        handler.heartbeat(Duration.ofSeconds(60));

        // assert
        assertThat(handler.getSessions(USER_ID)).hasSize(1);
        verify(phone, never()).sendMessage(pingFrame());
        verify(pc).sendMessage(pingFrame());
    }

    @Test
    @DisplayName("success case : 발행하는 메시지마다 새 eventId 를 붙인다")
    void 발행하는_메시지마다_새_eventId_를_붙인다() throws Exception {
        // arrange
        WebSocketSession pc = memberSession();

        // act
        handler.handleMessage(pc, new TextMessage("{\"type\":\"message\",\"channelId\":7,\"message\":\"하나\"}"));
        handler.handleMessage(pc, new TextMessage("{\"type\":\"message\",\"channelId\":7,\"message\":\"둘\"}"));
        handler.handleMessage(pc, new TextMessage("{\"type\":\"message\",\"channelId\":0,\"message\":\"형식 오류\"}"));

        // assert
        ArgumentCaptor<ChatMessageEvent> published = ArgumentCaptor.forClass(ChatMessageEvent.class);
        verify(kafkaProducerClient, times(2)).publishChatSendMessage(published.capture());
        String first = published.getAllValues().get(0).eventId();
        String second = published.getAllValues().get(1).eventId();
        assertThat(java.util.UUID.fromString(first)).isNotNull();
        assertThat(first).isNotEqualTo(second);
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
        return new ChatMessageDelivery("message-1",
            new ChatMessageEvent("event-1", 9L, 0L, message, System.currentTimeMillis(), List.of()),
            List.of(receiveUserIds), List.of());
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
