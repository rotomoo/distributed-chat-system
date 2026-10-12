package com.distributed.chat.system.chatting.base.handler;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.distributed.chat.system.chatting.web.controller.feign.KafkaProducerClient;
import com.fasterxml.jackson.databind.ObjectMapper;
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
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
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
import protobuf.EdaChatMessage.ChatMessageDelivery;

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

    private CustomTextWebSocketHandler handler;

    private final AtomicInteger sessionSequence = new AtomicInteger();

    private ExecutorService executor;

    @BeforeEach
    void setup() {
        lenient().when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        lenient().when(redisTemplate.opsForSet()).thenReturn(setOperations);
        handler = new CustomTextWebSocketHandler(redisTemplate, kafkaProducerClient, new ObjectMapper());
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
