package com.distributed.chat.system.chatting.base.handler;

import com.distributed.chat.system.chatting.web.controller.feign.KafkaProducerClient;
import com.distributed.chat.system.common.util.NetworkUtil;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
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

@Slf4j
@Component
@RequiredArgsConstructor
public class CustomTextWebSocketHandler extends TextWebSocketHandler {

    // 채널 기능 적용 전까지 모든 메시지는 전체 채널(0)로 발행
    private static final long DEFAULT_CHANNEL_ID = 0L;
    private static final int SEND_TIME_LIMIT_MS = 5000;
    private static final int SEND_BUFFER_SIZE_LIMIT = 512 * 1024;

    private final RedisTemplate redisTemplate;
    private final Map<String, WebSocketSession> webSocketSessionMap = new ConcurrentHashMap<>();
    private final KafkaProducerClient kafkaProducerClient;
    private final ObjectMapper objectMapper;

    @Override
    public void afterConnectionEstablished(WebSocketSession webSocketSession) throws Exception {
        log.info("{} connected", webSocketSession.getId());

        String serverIp = NetworkUtil.getServerIp();
        String chatServerUri = serverIp + ":" + webSocketSession.getLocalAddress().getPort();
        String userId = (String) webSocketSession.getAttributes().get("userId");
        webSocketSession.getAttributes().put("chatServerUri", chatServerUri);

        redisTemplate.opsForValue().set(userId, "ws://" + chatServerUri);
        redisTemplate.opsForSet().add("chatServerUri:" + chatServerUri, userId);

        // 컨슈머 전달(HTTP 스레드)과 다른 메시지 전송이 동시에 일어날 수 있어 thread-safe 하게 감싼다
        this.webSocketSessionMap.put(userId,
            new ConcurrentWebSocketSessionDecorator(webSocketSession, SEND_TIME_LIMIT_MS, SEND_BUFFER_SIZE_LIMIT));
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

        // 같은 유저가 그 사이 새 세션으로 재접속했다면 새 세션을 지우지 않는다
        WebSocketSession current = this.webSocketSessionMap.get(userId);
        if (current != null && current.getId().equals(webSocketSession.getId())) {
            this.webSocketSessionMap.remove(userId);
            redisTemplate.opsForSet().remove("chatServerUri:" + chatServerUri, userId);
        }
    }

    /**
     * 컨슈머가 전달한 메시지를 이 서버에 접속한 수신자들에게 전송
     */
    public void deliver(ChatMessageDelivery delivery) {
        ChatMessage chatMessage = delivery.getChatMessage();

        TextMessage textMessage;
        try {
            textMessage = new TextMessage(objectMapper.writeValueAsString(Map.of(
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
            WebSocketSession session = this.webSocketSessionMap.get(String.valueOf(receiveUserId));
            if (session == null || !session.isOpen()) {
                continue;
            }
            try {
                session.sendMessage(textMessage);
            } catch (IOException e) {
                log.warn("send fail userId : {}, error : {}", receiveUserId, e.getMessage());
            }
        }
    }
}
