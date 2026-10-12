package com.distributed.chat.system.kafka.consumer.service;

import java.util.Set;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cloud.client.ServiceInstance;
import org.springframework.cloud.client.discovery.DiscoveryClient;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.http.converter.protobuf.ProtobufHttpMessageConverter;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import protobuf.EdaChatMessage.ChatMessage;
import protobuf.EdaChatMessage.ChatMessageDelivery;

/**
 * 살아있는 채팅 서버 목록(Eureka)을 돌면서, 서버별 접속 유저(Redis Set "chatServerUri:{ip}:{port}")에게 한 번씩 전달한다.
 * <p>
 * 채팅 서버 목록은 Eureka 클라이언트가 주기적으로 받아 메모리에 캐시한 것을 쓰므로 메시지마다 조회 비용이 없다.
 * (이전에는 Redis SCAN 으로 키 전체를 훑어 서버 키를 찾았다 — 키 수에 비례해 느려짐)
 */
@Service
@Slf4j
public class ChatMessageDeliveryService {

    private static final String CHATTING_SERVICE_ID = "chatting";
    private static final String CHAT_SERVER_KEY_PREFIX = "chatServerUri:";

    private final RedisTemplate<String, Object> redisTemplate;
    private final DiscoveryClient discoveryClient;
    private final RestClient restClient;

    public ChatMessageDeliveryService(RedisTemplate<String, Object> redisTemplate,
        DiscoveryClient discoveryClient, RestClient.Builder restClientBuilder) {
        this.redisTemplate = redisTemplate;
        this.discoveryClient = discoveryClient;
        this.restClient = restClientBuilder
            .messageConverters(converters -> converters.add(0, new ProtobufHttpMessageConverter()))
            .build();
    }

    public void deliver(String messageId, ChatMessage chatMessage) {
        // TODO 채널 기능 적용 후 채널 멤버만 수신하도록 변경. 지금은 접속한 모든 유저가 수신자(전체 채널)
        for (ServiceInstance chatServer : discoveryClient.getInstances(CHATTING_SERVICE_ID)) {
            String chatServerUri = chatServer.getHost() + ":" + chatServer.getPort();

            Set<Object> userIds = redisTemplate.opsForSet().members(CHAT_SERVER_KEY_PREFIX + chatServerUri);
            if (userIds == null || userIds.isEmpty()) {
                continue;
            }

            ChatMessageDelivery delivery = ChatMessageDelivery.newBuilder()
                .setMessageId(messageId)
                .setChatMessage(chatMessage)
                .addAllReceiveUserIds(userIds.stream().map(id -> Long.parseLong(id.toString())).toList())
                .build();

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
}
