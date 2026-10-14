package com.distributed.chat.system.chatting.base.presence;

import com.distributed.chat.system.common.redis.ChatPresenceKeys;
import com.distributed.chat.system.common.redis.ChatServers;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cloud.client.discovery.DiscoveryClient;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.stereotype.Component;

/**
 * Redis {@code {userId}} = 그 유저의 기기들이 모여 있는 채팅 서버 ("ws://ip:port"). 이 키를 쓰는 건 채팅 서버뿐이다.
 * <p>
 * 유저 하나 = 서버 하나 : PC / 폰이 서로 다른 서버에 붙으면 메시지를 한쪽에만 보낼 수 있으므로 한 서버로 모은다.
 * <ul>
 *   <li>처음 붙는 기기 : SET NX 로 이 서버를 적는다 (동시에 다른 서버에 붙어도 한 곳만 이긴다)</li>
 *   <li>이미 이 서버 : 그대로 (같은 서버의 두 번째 기기)</li>
 *   <li>살아 있는 다른 서버 : 거절 → 소켓을 닫고 클라이언트가 다시 붙으면 api-gateway 가 그 서버로 보낸다</li>
 *   <li>죽은 서버(Eureka 에 없음) : 그 값일 때만 이 서버로 바꾼다 (compare-and-set). 그 사이 다른 서버가 먼저 바꿨으면 거절</li>
 *   <li>이 서버의 마지막 기기가 끊김 : 값이 아직 이 서버일 때만 지운다 (compare-and-delete). 다른 서버 값은 건드리지 않는다</li>
 * </ul>
 * Redis {@code presence:{userId}} = 접속 상태 ("online" | "away", TTL 60초). 접속 위치를 가진 서버만 쓴다.
 * <ul>
 *   <li>쓰기 / TTL 갱신 : 위치 키가 이 서버일 때만 SET EX (확인과 쓰기를 Lua 하나로). 다른 서버로 넘어간 뒤 늦게 도착한 쓰기가 덮어쓰지 않는다</li>
 *   <li>마지막 기기가 끊김 : 위치 키가 이 서버일 때만 위치 키와 상태 키를 함께 지운다. 둘 중 하나만 남는 순간이 없다</li>
 * </ul>
 * 값 비교는 RedisTemplate 의 값 직렬화(JSON 문자열) 그대로 Lua 에서 한다.
 * 두 키를 한 스크립트에서 다루므로 Redis Cluster 로 바꾸면 해시 태그로 같은 슬롯에 둬야 한다 (지금은 Sentinel)
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class UserLocationRegistry {

    static final RedisScript<Long> COMPARE_AND_SET = new DefaultRedisScript<>(
        "if redis.call('get', KEYS[1]) == ARGV[1] then redis.call('set', KEYS[1], ARGV[2]) return 1 end return 0", Long.class);

    /**
     * KEYS = [위치 키, 상태 키], ARGV = [이 서버 값]. 위치 키가 이 서버일 때만 두 키를 함께 지운다
     */
    static final RedisScript<Long> COMPARE_AND_DELETE = new DefaultRedisScript<>(
        "if redis.call('get', KEYS[1]) == ARGV[1] then return redis.call('del', KEYS[1], KEYS[2]) end return 0", Long.class);

    /**
     * KEYS = [위치 키, 상태 키], ARGV = [이 서버 값, 상태]. 위치 키가 이 서버일 때만 상태를 TTL 과 함께 쓴다
     * (TTL 은 값 직렬화를 거치지 않도록 스크립트에 숫자로 넣는다)
     */
    static final RedisScript<Long> COMPARE_AND_SET_PRESENCE = new DefaultRedisScript<>(
        "if redis.call('get', KEYS[1]) == ARGV[1] then redis.call('set', KEYS[2], ARGV[2], 'EX', "
            + ChatPresenceKeys.PRESENCE_TTL.toSeconds() + ") return 1 end return 0", Long.class);

    private final RedisTemplate redisTemplate;
    private final DiscoveryClient discoveryClient;

    /**
     * 이 유저의 접속 위치를 이 서버로 잡는다
     *
     * @param chatServer 이 서버 ip:port
     * @return 이 서버로 잡았으면 true, 다른 살아 있는 서버가 갖고 있으면 false
     */
    @SuppressWarnings("unchecked")
    public boolean claim(String userId, String chatServer) {
        String key = ChatPresenceKeys.userKey(userId);
        String me = ChatPresenceKeys.userKeyValue(chatServer);
        if (Boolean.TRUE.equals(redisTemplate.opsForValue().setIfAbsent(key, me))) {
            return true;
        }

        Object current = redisTemplate.opsForValue().get(key);
        if (me.equals(current)) {
            return true;
        }
        if (current == null) {
            // 그 사이 지워졌다 : 다시 NX
            return Boolean.TRUE.equals(redisTemplate.opsForValue().setIfAbsent(key, me));
        }
        if (ChatServers.alive(discoveryClient).contains(ChatPresenceKeys.chatServerOf(current.toString()))) {
            return false;
        }

        log.info("userId : {} was on dead chat server {}, take over", userId, current);
        Long changed = (Long) redisTemplate.execute(COMPARE_AND_SET, List.of(key), current, me);
        return Long.valueOf(1).equals(changed);
    }

    /**
     * 접속 상태를 쓰고 TTL 을 60초로 다시 잡는다. 위치 키가 이 서버일 때만
     *
     * @param presence {@link ChatPresenceKeys#ONLINE} | {@link ChatPresenceKeys#AWAY}
     * @return 썼으면 true, 위치 키가 이 서버가 아니라서(다른 서버로 넘어감 / 없음) 안 썼으면 false
     */
    @SuppressWarnings("unchecked")
    public boolean writePresence(String userId, String chatServer, String presence) {
        Long written = (Long) redisTemplate.execute(COMPARE_AND_SET_PRESENCE,
            List.of(ChatPresenceKeys.userKey(userId), ChatPresenceKeys.presenceKey(userId)),
            ChatPresenceKeys.userKeyValue(chatServer), presence);
        return Long.valueOf(1).equals(written);
    }

    /**
     * 이 서버의 마지막 기기가 끊겼을 때. 위치 키가 아직 이 서버일 때만 위치 키와 상태 키를 함께 지운다
     */
    @SuppressWarnings("unchecked")
    public void release(String userId, String chatServer) {
        redisTemplate.execute(COMPARE_AND_DELETE,
            List.of(ChatPresenceKeys.userKey(userId), ChatPresenceKeys.presenceKey(userId)),
            ChatPresenceKeys.userKeyValue(chatServer));
    }
}
