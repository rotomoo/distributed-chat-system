package com.distributed.chat.system.kafka.consumer.service;

import com.distributed.chat.system.common.redis.ChatPresenceKeys;
import com.distributed.chat.system.kafka.consumer.feign.ChatServerClient;
import com.distributed.chat.system.mongodb.collection.ChatMessage.Attachment;
import com.distributed.chat.system.mongodb.collection.ChatMessage.Reaction;
import java.net.URI;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.function.BiConsumer;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cloud.client.ServiceInstance;
import org.springframework.cloud.client.discovery.DiscoveryClient;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;
import protobuf.EdaChatMessage;
import protobuf.EdaChatMessage.ChatMessage;
import protobuf.EdaChatMessage.ChatMessageDelivery;
import protobuf.EdaChatMessage.ChatReactionDelivery;

/**
 * 살아있는 채팅 서버 목록(Eureka)을 돌면서, 서버마다 "그 서버에 접속한 유저 ∩ 채널 멤버" 에게 한 번씩 전달한다.
 * <pre>
 *   SINTER chatServerUri:{ip}:{port} channel:{channelId}:members
 * </pre>
 * <p>
 * 채팅 서버 목록은 Eureka 클라이언트가 주기적으로 받아 메모리에 캐시한 것을 쓰므로 메시지마다 조회 비용이 없다.
 * (이전에는 Redis SCAN 으로 키 전체를 훑어 서버 키를 찾았다 — 키 수에 비례해 느려짐)
 * <p>
 * 메시지와 리액션은 수신자를 고르는 방법이 같고, 보내는 내용과 채팅 서버 API 만 다르다.
 * <p>
 * 전달 실패는 예외로 알려서 Kafka 재시도 토픽(1s → 5s → 30s, 그 뒤 DLT)이 다시 전달하게 한다 (KafkaRetryTopicConfig)
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class ChatMessageDeliveryService {

    private static final String CHATTING_SERVICE_ID = "chatting";
    private static final String CHANNEL_MEMBERS_KEY_FORMAT = "channel:%d:members";

    private final RedisTemplate<String, Object> redisTemplate;
    private final DiscoveryClient discoveryClient;
    private final ChatServerClient chatServerClient;

    /**
     * @param attachments 컨슈머가 확인한 첨부 (없으면 빈 목록)
     */
    public void deliver(String messageId, ChatMessage chatMessage, List<Attachment> attachments) {
        List<EdaChatMessage.Attachment> attachmentProtos = attachments.stream()
            .map(attachment -> EdaChatMessage.Attachment.newBuilder()
                .setFileId(attachment.fileId())
                .setName(attachment.name() == null ? "" : attachment.name())
                .setContentType(attachment.contentType() == null ? "" : attachment.contentType())
                .setSize(attachment.size())
                .build())
            .toList();

        forEachChatServer(chatMessage.getChannelId(), (chatServerUri, receiveUserIds) ->
            chatServerClient.deliver(chatServerUri, ChatMessageDelivery.newBuilder()
                .setMessageId(messageId)
                .setChatMessage(chatMessage)
                .addAllReceiveUserIds(receiveUserIds)
                .addAllAttachments(attachmentProtos)
                .build()));
    }

    /**
     * @param reactions 바뀐 뒤의 그 메시지 reactions 전체 (클라이언트는 통째로 교체)
     */
    public void deliverReaction(Long channelId, String messageId, List<Reaction> reactions) {
        List<EdaChatMessage.Reaction> reactionProtos = reactions.stream()
            .map(reaction -> EdaChatMessage.Reaction.newBuilder()
                .setEmoji(reaction.emoji())
                .addAllUserIds(reaction.userIds() == null ? List.of() : reaction.userIds())
                .build())
            .toList();

        forEachChatServer(channelId, (chatServerUri, receiveUserIds) ->
            chatServerClient.deliverReaction(chatServerUri, ChatReactionDelivery.newBuilder()
                .setMessageId(messageId)
                .setChannelId(channelId)
                .addAllReactions(reactionProtos)
                .addAllReceiveUserIds(receiveUserIds)
                .build()));
    }

    /**
     * 모든 채팅 서버에 한 번씩 보내 보고, 한 곳이라도 실패하면 끝에서 {@link DeliveryFailedException} 을 던진다.
     * 한 서버 장애가 다른 서버 전달을 막지 않으며, 던진 예외로 Kafka 재시도 토픽이 나중에 다시 전달한다
     * (성공했던 서버도 다시 받지만 클라이언트가 messageId 로 걸러내고, 리액션은 전체 교체라 문제없다)
     */
    private void forEachChatServer(long channelId, BiConsumer<URI, List<Long>> send) {
        String channelMembersKey = CHANNEL_MEMBERS_KEY_FORMAT.formatted(channelId);
        List<String> failedChatServers = new ArrayList<>();

        for (ServiceInstance chatServer : discoveryClient.getInstances(CHATTING_SERVICE_ID)) {
            String chatServerUri = chatServer.getHost() + ":" + chatServer.getPort();

            Set<Object> userIds = redisTemplate.opsForSet().intersect(ChatPresenceKeys.chatServerKey(chatServerUri), channelMembersKey);
            if (userIds == null || userIds.isEmpty()) {
                continue;
            }

            try {
                send.accept(URI.create("http://" + chatServerUri), userIds.stream().map(id -> Long.parseLong(id.toString())).toList());
            } catch (Exception e) {
                log.warn("deliver fail chatServer : {}, error : {}", chatServerUri, e.getMessage());
                failedChatServers.add(chatServerUri);
            }
        }

        if (!failedChatServers.isEmpty()) {
            throw new DeliveryFailedException(failedChatServers);
        }
    }
}
