package com.distributed.chat.system.chatting.base.handler;

import com.distributed.chat.system.chatting.web.dto.ChatMessage;
import com.distributed.chat.system.common.util.NetworkUtil;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.security.Principal;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;

@Slf4j
@Component
public class CustomTextWebSocketHandler extends TextWebSocketHandler {

    private final RedisTemplate redisTemplate;
    private final Map<String, WebSocketSession> webSocketSessionMap = new ConcurrentHashMap<>();

    @Value("${spring.session.redis.namespace}")
    private String sessionNamespace;

    public CustomTextWebSocketHandler(RedisTemplate redisTemplate) {
        this.redisTemplate = redisTemplate;
    }


    @Override
    public void afterConnectionEstablished(WebSocketSession session) throws Exception {
        log.info("webSessionId = {} connected", session.getId());
        String sessionId = (String) session.getAttributes().get("sessionId");
        log.info("login http SessionId = {}", sessionId);

        String serverIp = NetworkUtil.getServerIp();

        Map sessionData = redisTemplate.opsForHash()
            .entries(sessionNamespace + ":sessions:" + sessionId);
        String userId = (String) sessionData.get("sessionAttr:userId");

        String chatServerUri = "ws://" + serverIp + ":" + session.getLocalAddress().getPort() + "/websocket";

        redisTemplate.opsForValue().set(userId, chatServerUri);

        this.webSocketSessionMap.put(session.getId(), session);
    }

    @Override
    public void handleMessage(WebSocketSession session, WebSocketMessage<?> message)
        throws Exception {
        Principal principal = session.getPrincipal();
        String sender = (principal != null) ? principal.getName() : "Anonymous";

        log.info("{} sent {}", session.getId(), message.getPayload());

        ChatMessage chatMessage = new ChatMessage(session.getId(), message.getPayload().toString());

        // {"sender":"fee1fa63-a0c2-d8e0-234a-510575341f9c","message":"124v14"}
        ObjectMapper objectMapper = new ObjectMapper();
        String jsonMessage = objectMapper.writeValueAsString(chatMessage);
        WebSocketMessage<String> newMessage = new TextMessage(jsonMessage);

        // fee1fa63-a0c2-d8e0-234a-510575341f9c:124
//        String plainMessage = session.getId() + ":" + message.getPayload();
//        WebSocketMessage<String> newMessage = new TextMessage(plainMessage);

        this.webSocketSessionMap.values().forEach(webSocketSession -> {
            try {
                webSocketSession.sendMessage(newMessage);
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        });
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status)
        throws Exception {
        log.info("{} disconnected", session.getId());

        this.webSocketSessionMap.remove(session.getId());
    }
}
