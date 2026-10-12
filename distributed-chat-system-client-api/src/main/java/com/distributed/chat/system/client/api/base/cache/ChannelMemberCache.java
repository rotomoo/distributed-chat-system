package com.distributed.chat.system.client.api.base.cache;

import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/**
 * 채널 멤버 Redis 캐시 : channel:{channelId}:members → { userId, ... }
 * <p>
 * 원본은 MySQL channel_user. 메시지 전달 시 채팅 서버별 접속자 Set 과 SINTER 해서 수신자를 고르고,
 * 채팅 서버가 보낼 권한(SISMEMBER)을 확인하는 데 쓴다.
 */
@Component
@RequiredArgsConstructor
public class ChannelMemberCache {

    private final RedisTemplate<String, Object> redisTemplate;

    public static String key(Long channelId) {
        return "channel:" + channelId + ":members";
    }

    /**
     * DB 트랜잭션이 커밋된 뒤에 캐시에 넣는다. 롤백됐는데 캐시에만 남으면 멤버가 아닌 사람에게 메시지가 갈 수 있다
     */
    public void add(Long channelId, Long userId) {
        Runnable addToCache = () -> redisTemplate.opsForSet().add(key(channelId), String.valueOf(userId));
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    addToCache.run();
                }
            });
            return;
        }
        addToCache.run();
    }
}
