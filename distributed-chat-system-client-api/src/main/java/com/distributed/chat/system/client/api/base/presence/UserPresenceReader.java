package com.distributed.chat.system.client.api.base.presence;

import com.distributed.chat.system.common.redis.ChatPresenceKeys;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Component;

/**
 * 지금 채팅 서버에 웹소켓이 하나라도 붙어 있는 유저를 찾는다. 채팅 서버가 쓰는 Redis 접속 정보를 읽기만 한다.
 * <ul>
 *   <li>{@code {userId}} → "ws://ip:port" : 마지막으로 붙은 서버 (끊겨도 남는다)</li>
 *   <li>{@code chatServerUri:{ip:port}} : 그 서버에 지금 연결된 userId Set (마지막 기기가 끊기면 빠진다)</li>
 * </ul>
 * 온라인 = 값이 있고 그 서버의 Set 에 들어 있다. 조회는 MGET 1번 + 서버별 SMISMEMBER 1번
 */
@Component
@RequiredArgsConstructor
public class UserPresenceReader {

    private final RedisTemplate<String, Object> redisTemplate;

    public Set<Long> findOnlineUserIds(Collection<Long> userIds) {
        if (userIds.isEmpty()) {
            return Set.of();
        }

        List<String> keys = userIds.stream().map(ChatPresenceKeys::userKey).toList();
        List<Object> chatServerUris = redisTemplate.opsForValue().multiGet(keys);
        if (chatServerUris == null) {
            return Set.of();
        }

        Map<String, List<String>> userIdsByChatServer = new HashMap<>();
        for (int i = 0; i < keys.size(); i++) {
            if (chatServerUris.get(i) instanceof String chatServerUri && !chatServerUri.isBlank()) {
                userIdsByChatServer.computeIfAbsent(ChatPresenceKeys.chatServerOf(chatServerUri), server -> new ArrayList<>()).add(keys.get(i));
            }
        }

        Set<Long> onlineUserIds = new HashSet<>();
        userIdsByChatServer.forEach((chatServer, candidates) -> {
            Map<Object, Boolean> connected = redisTemplate.opsForSet()
                .isMember(ChatPresenceKeys.chatServerKey(chatServer), candidates.toArray());
            if (connected == null) {
                return;
            }
            connected.forEach((userId, member) -> {
                if (Boolean.TRUE.equals(member)) {
                    onlineUserIds.add(Long.valueOf(userId.toString()));
                }
            });
        });
        return onlineUserIds;
    }
}
