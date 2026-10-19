package com.distributed.chat.system.message.consumer.service;

import com.distributed.chat.system.common.event.ChatMessageDelivery;
import com.distributed.chat.system.common.event.ChatMessageEvent;
import com.distributed.chat.system.common.event.ChatReactionDelivery;
import com.distributed.chat.system.common.redis.ChatPresenceKeys;
import com.distributed.chat.system.common.redis.ChatPresenceKeys.ChatPresence;
import com.distributed.chat.system.common.redis.ChatServers;
import com.distributed.chat.system.message.consumer.feign.ChatServerClient;
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

/**
 * 채널 멤버(호출하는 쪽이 MySQL channel_user 에서 읽어 넘김)에게 메시지 / 리액션을 전달한다.
 * <ol>
 *   <li>접속 위치 : 멤버들의 Redis {@code {userId}} = {server, status} 를 MGET 1번 (server 만 본다, 예전 값 "ws://..." 도 읽는다) (멤버가 아주 많으면 500 개씩 나눠 파이프라인 1번)</li>
 *   <li>값이 없거나 가리키는 서버가 Eureka 에 없으면(죽은 서버) 오프라인 → 보내지 않고 {@link ChatReceivers#offline()} 로 돌려준다
 *       (메시지는 호출하는 쪽이 웹 푸시 알림 이벤트로 넘긴다, 리액션은 알림 없음)</li>
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
     * @param receivers   {@link #locate} 결과. 접속한 멤버(보낸 사람 포함 — 보낸 사람의 다른 기기에도 간다)에게만 보낸다
     */
    public void deliver(String messageId, ChatMessageEvent chatMessage, List<Attachment> attachments, ChatReceivers receivers) {
        // 이름 / 형식이 없는 첨부는 "" 로 간다 (ChatMessageDelivery.Attachment)
        List<ChatMessageDelivery.Attachment> deliveredAttachments = attachments.stream()
            .map(attachment -> new ChatMessageDelivery.Attachment(
                attachment.fileId(), attachment.name(), attachment.contentType(), attachment.size()))
            .toList();

        deliverToChatServers(receivers.byChatServer(), (chatServerUri, receiveUserIds) ->
            chatServerClient.deliver(chatServerUri,
                new ChatMessageDelivery(messageId, chatMessage, receiveUserIds, deliveredAttachments)));
    }

    /**
     * @param reactions 바뀐 뒤의 그 메시지 reactions 전체 (클라이언트는 통째로 교체)
     */
    public void deliverReaction(Long channelId, String messageId, List<Reaction> reactions, Collection<Long> memberIds) {
        // 누른 사람 목록이 없으면 빈 목록 (ChatReactionDelivery.Reaction)
        List<ChatReactionDelivery.Reaction> deliveredReactions = reactions.stream()
            .map(reaction -> new ChatReactionDelivery.Reaction(reaction.emoji(), reaction.userIds()))
            .toList();

        deliverToChatServers(locate(memberIds).byChatServer(), (chatServerUri, receiveUserIds) ->
            chatServerClient.deliverReaction(chatServerUri,
                new ChatReactionDelivery(messageId, channelId, deliveredReactions, receiveUserIds)));
    }

    private void deliverToChatServers(Map<String, List<Long>> receiversByChatServer, BiConsumer<URI, List<Long>> send) {
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
     * 멤버 → 접속한 채팅 서버(ip:port) / 오프라인(값 없음 / 죽은 서버)
     */
    public ChatReceivers locate(Collection<Long> memberIds) {
        List<Long> ids = List.copyOf(memberIds);
        if (ids.isEmpty()) {
            return new ChatReceivers(Map.of(), List.of());
        }
        List<Object> locations = lookupLocations(ids);
        Set<String> aliveChatServers = ChatServers.alive(discoveryClient);

        Map<String, List<Long>> receiversByChatServer = new LinkedHashMap<>();
        List<Long> offline = new ArrayList<>();
        for (int i = 0; i < ids.size(); i++) {
            ChatPresence presence = ChatPresenceKeys.parse(i < locations.size() ? locations.get(i) : null);
            if (presence == null) {
                // 오프라인 : 앱이 꺼져 있다 (끊김 / 채팅 서버가 죽은 뒤 TTL 60초가 지남)
                offline.add(ids.get(i));
                continue;
            }
            String chatServer = presence.chatServer();
            if (!aliveChatServers.contains(chatServer)) {
                // 죽은 서버를 가리키는 값 = 오프라인 (서버가 죽어 키를 지우지 못했다)
                log.debug("offline userId : {} (dead chat server {})", ids.get(i), chatServer);
                offline.add(ids.get(i));
                continue;
            }
            receiversByChatServer.computeIfAbsent(chatServer, server -> new ArrayList<>()).add(ids.get(i));
        }
        return new ChatReceivers(receiversByChatServer, offline);
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
