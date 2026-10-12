package com.distributed.chat.system.kafka.consumer.service;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.Cursor;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ScanOptions;
import org.springframework.http.converter.protobuf.ProtobufHttpMessageConverter;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import protobuf.EdaChatMessage.ChatMessage;
import protobuf.EdaChatMessage.ChatMessageDelivery;

/**
 * 채팅 서버별 접속 유저(Redis Set "chatServerUri:{ip}:{port}")를 조회해서, 각 채팅 서버에 한 번씩 전달한다.
 */
@Service
@Slf4j
public class ChatMessageDeliveryService {

    private static final String CHAT_SERVER_KEY_PREFIX = "chatServerUri:";

    private final RedisTemplate<String, Object> redisTemplate;
    private final RestClient restClient;

    public ChatMessageDeliveryService(RedisTemplate<String, Object> redisTemplate,
        RestClient.Builder restClientBuilder) {
        this.redisTemplate = redisTemplate;
        this.restClient = restClientBuilder
            .messageConverters(converters -> converters.add(0, new ProtobufHttpMessageConverter()))
            .build();
    }

    public void deliver(String messageId, ChatMessage chatMessage) {
        // TODO 채널 기능 적용 후 채널 멤버만 수신하도록 변경. 지금은 접속한 모든 유저가 수신자(전체 채널)
        for (String chatServerKey : findChatServerKeys()) {
            Set<Object> userIds = redisTemplate.opsForSet().members(chatServerKey);
            if (userIds == null || userIds.isEmpty()) {
                continue;
            }

            ChatMessageDelivery delivery = ChatMessageDelivery.newBuilder()
                .setMessageId(messageId)
                .setChatMessage(chatMessage)
                .addAllReceiveUserIds(userIds.stream().map(id -> Long.parseLong(id.toString())).toList())
                .build();

            String chatServerUri = chatServerKey.substring(CHAT_SERVER_KEY_PREFIX.length());
            try {
                restClient.post()
                    .uri("http://" + chatServerUri + "/internal/chat/deliver")
                    .contentType(ProtobufHttpMessageConverter.PROTOBUF)
                    .body(delivery)
                    .retrieve()
                    .toBodilessEntity();
            } catch (Exception e) {
                // 한 채팅 서버 장애가 다른 서버 전달을 막지 않도록 로그만 남긴다
                log.warn("deliver fail chatServer : {}, error : {}", chatServerUri, e.getMessage());
            }
        }
    }

    private List<String> findChatServerKeys() {
        List<String> keys = new ArrayList<>();
        ScanOptions options = ScanOptions.scanOptions().match(CHAT_SERVER_KEY_PREFIX + "*").count(100).build();
        try (Cursor<String> cursor = redisTemplate.scan(options)) {
            cursor.forEachRemaining(keys::add);
        }
        return keys;
    }
}
