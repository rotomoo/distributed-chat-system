package com.distributed.chat.system.chatting.base.handler;

import com.distributed.chat.system.chatting.web.controller.feign.KafkaProducerClient;
import com.distributed.chat.system.common.util.NetworkUtil;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.WebSocketMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;
import protobuf.EdaChatMessage.ChatMessage;

@Slf4j
@Component
@RequiredArgsConstructor
public class CustomTextWebSocketHandler extends TextWebSocketHandler {

    private final RedisTemplate redisTemplate;
    private final Map<String, WebSocketSession> webSocketSessionMap = new ConcurrentHashMap<>();
    private final KafkaProducerClient kafkaProducerClient;

    @Override
    public void afterConnectionEstablished(WebSocketSession webSocketSession) throws Exception {
        log.info("{} connected", webSocketSession.getId());

        String serverIp = NetworkUtil.getServerIp();
        String chatServerUri = serverIp + ":" + webSocketSession.getLocalAddress().getPort();
        String userId = (String) webSocketSession.getAttributes().get("userId");

        redisTemplate.opsForValue().set(userId, "ws://" + chatServerUri);
        redisTemplate.opsForSet().add("chatServerUri:" + chatServerUri, userId);

        this.webSocketSessionMap.put(userId, webSocketSession);
    }

    @Override
    public void handleMessage(WebSocketSession webSocketSession, WebSocketMessage<?> message) throws Exception {
        log.info("{} sent {}", webSocketSession.getId(), message.getPayload());

        String userId = (String) webSocketSession.getAttributes().get("userId");

        ChatMessage chatMessage = ChatMessage.newBuilder()
            .setSendUserId(Long.parseLong(userId))
            .setMessage(message.getPayload().toString())
            .build();
        
        kafkaProducerClient.publishChatSendMessage(chatMessage);
    }

    @Override
    public void afterConnectionClosed(WebSocketSession webSocketSession, CloseStatus status)
        throws Exception {
        log.info("{} disconnected", webSocketSession.getId());

        String userId = (String) webSocketSession.getAttributes().get("userId");

        this.webSocketSessionMap.remove(userId);
    }
}
