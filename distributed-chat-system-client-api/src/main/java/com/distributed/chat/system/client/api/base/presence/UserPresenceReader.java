package com.distributed.chat.system.client.api.base.presence;

import com.distributed.chat.system.common.redis.ChatPresenceKeys;
import com.distributed.chat.system.common.redis.ChatServers;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springframework.cloud.client.discovery.DiscoveryClient;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Component;

/**
 * 지금 채팅 서버에 웹소켓이 하나라도 붙어 있는 유저를 찾는다. 채팅 서버가 쓰는 Redis 접속 위치를 읽기만 한다.
 * <p>
 * 온라인 = {@code {userId}} 값("ws://ip:port")이 있고, 그 서버가 지금 Eureka 에 살아 있다.
 * 채팅 서버는 그 서버의 마지막 기기가 끊길 때 키를 지우지만, 서버가 죽으면 지울 수 없으므로 죽은 서버를 가리키는 값은 오프라인이다.
 * 조회는 팀 멤버 전체를 MGET 1번
 */
@Component
@RequiredArgsConstructor
public class UserPresenceReader {

    private final RedisTemplate<String, Object> redisTemplate;
    private final DiscoveryClient discoveryClient;

    public Set<Long> findOnlineUserIds(Collection<Long> userIds) {
        if (userIds.isEmpty()) {
            return Set.of();
        }

        List<Long> ids = List.copyOf(userIds);
        List<Object> chatServerUris = redisTemplate.opsForValue().multiGet(ids.stream().map(ChatPresenceKeys::userKey).toList());
        if (chatServerUris == null) {
            return Set.of();
        }

        Set<String> aliveChatServers = ChatServers.alive(discoveryClient);
        Set<Long> onlineUserIds = new HashSet<>();
        for (int i = 0; i < ids.size(); i++) {
            if (chatServerUris.get(i) instanceof String chatServerUri
                && aliveChatServers.contains(ChatPresenceKeys.chatServerOf(chatServerUri))) {
                onlineUserIds.add(ids.get(i));
            }
        }
        return onlineUserIds;
    }
}
