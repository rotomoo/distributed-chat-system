package com.distributed.chat.system.chatting.base.handler;

import com.distributed.chat.system.chatting.web.controller.feign.KafkaProducerClient;
import com.distributed.chat.system.common.util.NetworkUtil;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.ConcurrentWebSocketSessionDecorator;
import org.springframework.web.socket.handler.TextWebSocketHandler;
import protobuf.EdaChatMessage.ChatMessage;
import protobuf.EdaChatMessage.ChatMessageDelivery;

/**
 * 한 유저가 여러 기기(PC, 폰, 탭)로 동시에 접속할 수 있다.
 * <ul>
 *   <li>서버 메모리 : userId → { 웹소켓 세션ID → 세션 } (기기마다 소켓이 따로 있으므로 모두 보관)</li>
 *   <li>Redis {@code chatServerUri:{ip:port}} : 이 서버에 접속한 userId Set. 이 서버에서 그 유저의 마지막 소켓이 끊길 때만 뺀다</li>
 *   <li>Redis {@code {userId}} : 마지막으로 붙은 채팅 서버 (api-gateway 라우팅 참고용)</li>
 * </ul>
 * {@code chatServerUri:{ip:port}} 는 이 서버만 쓰는 키라서 서버 간 경쟁이 없고, 서버 안의 경쟁(같은 유저의 동시 접속/해제)은
 * {@link ConcurrentHashMap#compute} 로 유저 단위 직렬화한다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class CustomTextWebSocketHandler extends TextWebSocketHandler {

    // 채널 기능 적용 전까지 모든 메시지는 전체 채널(0)로 발행
    private static final long DEFAULT_CHANNEL_ID = 0L;
    private static final int SEND_TIME_LIMIT_MS = 5000;
    private static final int SEND_BUFFER_SIZE_LIMIT = 512 * 1024;
    private static final String CHAT_SERVER_KEY_PREFIX = "chatServerUri:";

    private final RedisTemplate redisTemplate;
    private final Map<String, Map<String, WebSocketSession>> webSocketSessionMap = new ConcurrentHashMap<>();
    private final KafkaProducerClient kafkaProducerClient;
    private final ObjectMapper objectMapper;

    @Override
    public void afterConnectionEstablished(WebSocketSession webSocketSession) throws Exception {
        log.info("{} connected", webSocketSession.getId());

        String serverIp = NetworkUtil.getServerIp();
        String chatServerUri = serverIp + ":" + webSocketSession.getLocalAddress().getPort();
        String userId = (String) webSocketSession.getAttributes().get("userId");
        webSocketSession.getAttributes().put("chatServerUri", chatServerUri);

        // 컨슈머 전달(HTTP 스레드)과 다른 메시지 전송이 동시에 일어날 수 있어 thread-safe 하게 감싼다
        WebSocketSession concurrentSession = new ConcurrentWebSocketSessionDecorator(
            webSocketSession, SEND_TIME_LIMIT_MS, SEND_BUFFER_SIZE_LIMIT);

        // 같은 유저의 접속/해제가 끼어들지 않도록 Redis 갱신까지 유저 단위로 묶는다
        this.webSocketSessionMap.compute(userId, (key, sessions) -> {
            Map<String, WebSocketSession> userSessions = sessions == null ? new ConcurrentHashMap<>() : sessions;
            userSessions.put(webSocketSession.getId(), concurrentSession);

            redisTemplate.opsForValue().set(userId, "ws://" + chatServerUri);
            redisTemplate.opsForSet().add(CHAT_SERVER_KEY_PREFIX + chatServerUri, userId);
            return userSessions;
        });

        // 접속 정보 기록까지 끝났음을 알린다. 클라이언트는 이 신호를 받아야 "연결됨"으로 본다.
        // (api-gateway 는 채팅 서버에 붙기 전에 클라이언트에 101 을 먼저 보내므로, 101 만으로는 실제 연결을 보장하지 못한다)
        concurrentSession.sendMessage(new TextMessage(objectMapper.writeValueAsString(Map.of(
            "type", "connected",
            "chatServer", chatServerUri
        ))));
    }

    @Override
    public void handleMessage(WebSocketSession webSocketSession, WebSocketMessage<?> message) throws Exception {
        log.info("{} sent {}", webSocketSession.getId(), message.getPayload());

        String userId = (String) webSocketSession.getAttributes().get("userId");

        ChatMessage chatMessage = ChatMessage.newBuilder()
            .setSendUserId(Long.parseLong(userId))
            .setChannelId(DEFAULT_CHANNEL_ID)
            .setMessage(message.getPayload().toString())
            .setPublishDt(System.currentTimeMillis())
            .build();

        kafkaProducerClient.publishChatSendMessage(chatMessage);
    }

    @Override
    public void afterConnectionClosed(WebSocketSession webSocketSession, CloseStatus status)
        throws Exception {
        log.info("{} disconnected", webSocketSession.getId());

        String userId = (String) webSocketSession.getAttributes().get("userId");
        String chatServerUri = (String) webSocketSession.getAttributes().get("chatServerUri");

        // 끊긴 기기만 뺀다. 이 서버에서 그 유저의 마지막 기기일 때만 Redis 접속 정보에서 제거
        // ({userId} 라우팅 키는 남겨서 재접속 시 같은 서버로 오게 한다)
        this.webSocketSessionMap.computeIfPresent(userId, (key, userSessions) -> {
            userSessions.remove(webSocketSession.getId());
            if (!userSessions.isEmpty()) {
                return userSessions;
            }
            redisTemplate.opsForSet().remove(CHAT_SERVER_KEY_PREFIX + chatServerUri, userId);
            return null;
        });
    }

    /**
     * 컨슈머가 전달한 메시지를 이 서버에 접속한 수신자들의 모든 기기에 전송
     */
    public void deliver(ChatMessageDelivery delivery) {
        ChatMessage chatMessage = delivery.getChatMessage();

        TextMessage textMessage;
        try {
            textMessage = new TextMessage(objectMapper.writeValueAsString(Map.of(
                "type", "message",
                "messageId", delivery.getMessageId(),
                "channelId", chatMessage.getChannelId(),
                "sendUserId", chatMessage.getSendUserId(),
                "message", chatMessage.getMessage(),
                "publishDt", chatMessage.getPublishDt()
            )));
        } catch (IOException e) {
            log.error("chat message serialize error : {}", e.getMessage());
            return;
        }

        for (Long receiveUserId : delivery.getReceiveUserIdsList()) {
            for (WebSocketSession session : getSessions(String.valueOf(receiveUserId))) {
                if (!session.isOpen()) {
                    continue;
                }
                try {
                    session.sendMessage(textMessage);
                } catch (IOException e) {
                    log.warn("send fail userId : {}, session : {}, error : {}", receiveUserId, session.getId(), e.getMessage());
                }
            }
        }
    }

    /**
     * 이 서버에 붙어 있는 유저의 기기(소켓) 목록
     */
    public Collection<WebSocketSession> getSessions(String userId) {
        Map<String, WebSocketSession> userSessions = this.webSocketSessionMap.get(userId);
        return userSessions == null ? List.of() : userSessions.values();
    }
}
