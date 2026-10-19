package com.distributed.chat.system.notification.consumer.service;

import com.distributed.chat.system.common.redis.NotificationKeys;
import java.nio.charset.StandardCharsets;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.connection.RedisStringCommands.SetOption;
import org.springframework.data.redis.core.RedisCallback;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.types.Expiration;
import org.springframework.stereotype.Component;

/**
 * 같은 (유저, 채널) 웹 푸시는 30초에 한 번 : Redis SET notify:throttle:{userId}:{channelId} 1 NX EX 30.
 * 받는 사람 수만큼의 SET NX 를 파이프라인 한 번으로 보낸다 (이벤트 한 건 = 왕복 1번)
 */
@Component
@RequiredArgsConstructor
public class NotificationThrottle {

    private static final byte[] VALUE = "1".getBytes(StandardCharsets.UTF_8);

    private final StringRedisTemplate stringRedisTemplate;

    /**
     * @return 이번에 보내도 되는 유저 (키를 새로 잡은 유저). 30초 안에 이미 보낸 유저는 빠진다
     */
    public Set<Long> acquire(Collection<Long> userIds, Long channelId) {
        List<Long> ids = List.copyOf(new LinkedHashSet<>(userIds));
        if (ids.isEmpty()) {
            return Set.of();
        }
        List<Object> results = stringRedisTemplate.executePipelined((RedisCallback<Object>) connection -> {
            for (Long userId : ids) {
                connection.stringCommands().set(NotificationKeys.throttleKey(userId, channelId).getBytes(StandardCharsets.UTF_8),
                    VALUE, Expiration.seconds(NotificationKeys.THROTTLE_SECONDS), SetOption.SET_IF_ABSENT);
            }
            return null;
        });

        Set<Long> acquired = new LinkedHashSet<>();
        for (int i = 0; i < ids.size(); i++) {
            if (i < results.size() && Boolean.TRUE.equals(results.get(i))) {
                acquired.add(ids.get(i));
            }
        }
        return acquired;
    }
}
