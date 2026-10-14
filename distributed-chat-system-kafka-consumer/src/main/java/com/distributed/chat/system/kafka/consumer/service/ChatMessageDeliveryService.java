package com.distributed.chat.system.kafka.consumer.service;

import com.distributed.chat.system.common.redis.ChatPresenceKeys;
import com.distributed.chat.system.common.redis.ChatServers;
import com.distributed.chat.system.kafka.consumer.feign.ChatServerClient;
import com.distributed.chat.system.mongodb.collection.ChatMessage.Attachment;
import com.distributed.chat.system.mongodb.collection.ChatMessage.Reaction;
import java.net.URI;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.Executor;
import java.util.function.BiConsumer;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.cloud.client.discovery.DiscoveryClient;
import org.springframework.data.redis.core.RedisCallback;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.serializer.RedisSerializer;
import org.springframework.stereotype.Service;
import protobuf.EdaChatMessage;
import protobuf.EdaChatMessage.ChatMessage;
import protobuf.EdaChatMessage.ChatMessageDelivery;
import protobuf.EdaChatMessage.ChatReactionDelivery;

/**
 * 채널 멤버(호출하는 쪽이 MySQL channel_user 에서 읽어 넘김)에게 메시지 / 리액션을 전달한다.
 * <ol>
 *   <li>접속 위치 : 멤버들의 Redis {@code {userId}} 를 MGET 1번 (멤버가 아주 많으면 500 개씩 나눠 파이프라인 1번)</li>
 *   <li>값이 없거나 가리키는 서버가 Eureka 에 없으면(죽은 서버) 오프라인 → 보내지 않는다 (푸시 알림이 붙을 자리)</li>
 *   <li>채팅 서버별로 묶어서 서버당 500 명씩 보낸다. 서로 다른 서버는 동시에 보내고 모두 끝날 때까지 기다린다
 *       (이 레코드 처리가 끝나야 같은 채널의 다음 레코드를 처리하므로 채널 순서가 지켜진다)</li>
 *   <li>한 서버라도 실패하면 끝에서 {@link DeliveryFailedException} → Kafka 재시도 토픽이 다시 전달한다
 *       (성공했던 서버도 다시 받지만 클라이언트가 messageId 로 걸러내고, 리액션은 전체 교체라 문제없다)</li>
 * </ol>
 */
@Service
@Slf4j
public class ChatMessageDeliveryService {

    /**
     * MGET 한 번에 묻는 키 수. 넘으면 이 크기로 나눠 파이프라인으로 한 번에 보낸다
     */
    static final int LOCATION_LOOKUP_CHUNK = 500;

    /**
     * 채팅 서버에 한 번에 보내는 수신자 수 (설계 노트 5장 "전송 묶음 크기"). 큰 채널의 요청 하나가 너무 커지지 않게 한다
     */
    static final int DELIVERY_CHUNK = 500;

    private final RedisTemplate<String, Object> redisTemplate;
    private final DiscoveryClient discoveryClient;
    private final ChatServerClient chatServerClient;
    private final Executor chatDeliveryExecutor;

    public ChatMessageDeliveryService(RedisTemplate<String, Object> redisTemplate, DiscoveryClient discoveryClient,
        ChatServerClient chatServerClient, @Qualifier(DeliveryExecutorConfig.EXECUTOR) Executor chatDeliveryExecutor) {
        this.redisTemplate = redisTemplate;
        this.discoveryClient = discoveryClient;
        this.chatServerClient = chatServerClient;
        this.chatDeliveryExecutor = chatDeliveryExecutor;
    }

    /**
     * @param attachments 컨슈머가 확인한 첨부 (없으면 빈 목록)
     * @param memberIds   채널 멤버 (보낸 사람 포함 — 보낸 사람의 다른 기기에도 간다)
     */
    public void deliver(String messageId, ChatMessage chatMessage, List<Attachment> attachments, Collection<Long> memberIds) {
        List<EdaChatMessage.Attachment> attachmentProtos = attachments.stream()
            .map(attachment -> EdaChatMessage.Attachment.newBuilder()
                .setFileId(attachment.fileId())
                .setName(attachment.name() == null ? "" : attachment.name())
                .setContentType(attachment.contentType() == null ? "" : attachment.contentType())
                .setSize(attachment.size())
                .build())
            .toList();

        deliverToMembers(memberIds, (chatServerUri, receiveUserIds) ->
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
    public void deliverReaction(Long channelId, String messageId, List<Reaction> reactions, Collection<Long> memberIds) {
        List<EdaChatMessage.Reaction> reactionProtos = reactions.stream()
            .map(reaction -> EdaChatMessage.Reaction.newBuilder()
                .setEmoji(reaction.emoji())
                .addAllUserIds(reaction.userIds() == null ? List.of() : reaction.userIds())
                .build())
            .toList();

        deliverToMembers(memberIds, (chatServerUri, receiveUserIds) ->
            chatServerClient.deliverReaction(chatServerUri, ChatReactionDelivery.newBuilder()
                .setMessageId(messageId)
                .setChannelId(channelId)
                .addAllReactions(reactionProtos)
                .addAllReceiveUserIds(receiveUserIds)
                .build()));
    }

    private void deliverToMembers(Collection<Long> memberIds, BiConsumer<URI, List<Long>> send) {
        Map<String, List<Long>> receiversByChatServer = locate(memberIds);
        Queue<String> failedChatServers = new ConcurrentLinkedQueue<>();

        CompletableFuture<?>[] deliveries = receiversByChatServer.entrySet().stream()
            .map(entry -> CompletableFuture.runAsync(() -> {
                URI chatServerUri = URI.create("http://" + entry.getKey());
                for (List<Long> chunk : chunks(entry.getValue(), DELIVERY_CHUNK)) {
                    try {
                        send.accept(chatServerUri, chunk);
                    } catch (Exception e) {
                        log.warn("deliver fail chatServer : {}, error : {}", entry.getKey(), e.getMessage());
                        failedChatServers.add(entry.getKey());
                        return;
                    }
                }
            }, chatDeliveryExecutor))
            .toArray(CompletableFuture[]::new);
        CompletableFuture.allOf(deliveries).join();

        if (!failedChatServers.isEmpty()) {
            throw new DeliveryFailedException(List.copyOf(failedChatServers));
        }
    }

    /**
     * 멤버 → 접속한 채팅 서버(ip:port). 오프라인(값 없음 / 죽은 서버)은 빠진다
     */
    Map<String, List<Long>> locate(Collection<Long> memberIds) {
        List<Long> ids = List.copyOf(memberIds);
        if (ids.isEmpty()) {
            return Map.of();
        }
        List<Object> locations = lookupLocations(ids);
        Set<String> aliveChatServers = ChatServers.alive(discoveryClient);

        Map<String, List<Long>> receiversByChatServer = new LinkedHashMap<>();
        for (int i = 0; i < ids.size(); i++) {
            Object location = i < locations.size() ? locations.get(i) : null;
            if (!(location instanceof String chatServerUri)) {
                // 오프라인 : 앱이 꺼져 있다. 푸시 알림은 여기서 이 유저들에게 보내면 된다
                log.debug("offline userId : {}", ids.get(i));
                continue;
            }
            String chatServer = ChatPresenceKeys.chatServerOf(chatServerUri);
            if (!aliveChatServers.contains(chatServer)) {
                // 죽은 서버를 가리키는 값 = 오프라인 (서버가 죽어 키를 지우지 못했다)
                log.debug("offline userId : {} (dead chat server {})", ids.get(i), chatServer);
                continue;
            }
            receiversByChatServer.computeIfAbsent(chatServer, server -> new ArrayList<>()).add(ids.get(i));
        }
        return receiversByChatServer;
    }

    /**
     * 키 순서대로 값 (없으면 null). 500 개 이하면 MGET 1번, 넘으면 500 개씩 MGET 을 파이프라인으로 한 번에
     */
    @SuppressWarnings("unchecked")
    List<Object> lookupLocations(List<Long> ids) {
        List<String> keys = ids.stream().map(ChatPresenceKeys::userKey).toList();
        if (keys.size() <= LOCATION_LOOKUP_CHUNK) {
            List<Object> values = redisTemplate.opsForValue().multiGet(keys);
            return values == null ? List.of() : values;
        }

        RedisSerializer<String> keySerializer = (RedisSerializer<String>) redisTemplate.getKeySerializer();
        List<List<String>> keyChunks = chunks(keys, LOCATION_LOOKUP_CHUNK);
        List<Object> results = redisTemplate.executePipelined((RedisCallback<Object>) connection -> {
            for (List<String> keyChunk : keyChunks) {
                connection.stringCommands().mGet(keyChunk.stream().map(keySerializer::serialize).toArray(byte[][]::new));
            }
            return null;
        });

        List<Object> values = new ArrayList<>(keys.size());
        for (int c = 0; c < keyChunks.size(); c++) {
            Object result = c < results.size() ? results.get(c) : null;
            values.addAll(result instanceof List<?> list ? list : Arrays.asList(new Object[keyChunks.get(c).size()]));
        }
        return values;
    }

    static <T> List<List<T>> chunks(List<T> items, int size) {
        List<List<T>> chunks = new ArrayList<>();
        for (int from = 0; from < items.size(); from += size) {
            chunks.add(items.subList(from, Math.min(from + size, items.size())));
        }
        return chunks;
    }
}
