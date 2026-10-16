package com.distributed.chat.system.chatting.base.presence;

import com.distributed.chat.system.common.redis.ChatPresenceKeys;
import com.distributed.chat.system.common.redis.ChatPresenceKeys.ChatPresence;
import com.distributed.chat.system.common.redis.ChatServers;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cloud.client.discovery.DiscoveryClient;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.stereotype.Component;

/**
 * Redis {@code {userId}} = {"server":"ws://ip:port","status":"online"|"away"}, TTL 60초 ({@link ChatPresenceKeys}).
 * 그 유저의 기기들이 모여 있는 채팅 서버와 접속 상태를 키 하나에 둔다. 이 키를 쓰는 건 채팅 서버뿐이다.
 * <p>
 * 유저 하나 = 서버 하나 : PC / 폰이 서로 다른 서버에 붙으면 메시지를 한쪽에만 보낼 수 있으므로 한 서버로 모은다.
 * <ul>
 *   <li>처음 붙는 기기 : SET NX EX 60 으로 이 서버를 적는다 (동시에 다른 서버에 붙어도 한 곳만 이긴다)</li>
 *   <li>이미 이 서버 : 그대로 (같은 서버의 두 번째 기기)</li>
 *   <li>살아 있는 다른 서버 : 거절 → 소켓을 닫고 클라이언트가 다시 붙으면 api-gateway 가 그 서버로 보낸다</li>
 *   <li>죽은 서버(Eureka 에 없음) : 그 값일 때만 이 서버로 바꾼다 (compare-and-set). 그 사이 다른 서버가 먼저 바꿨으면 거절</li>
 *   <li>상태 변경 / TTL 갱신 : 값의 server 가 이 서버일 때만 새 값을 EX 60 으로 쓴다. 다른 서버로 넘어갔으면 건드리지 않는다</li>
 *   <li>이 서버의 마지막 기기가 끊김 : 값의 server 가 이 서버일 때만 지운다 (compare-and-delete)</li>
 *   <li>이 서버가 갑자기 죽음 : 아무도 갱신하지 않으므로 60초 뒤 위치와 상태가 함께 사라진다</li>
 * </ul>
 * 값은 JSON 텍스트 그대로 쓰고 읽는다 ({@link StringRedisTemplate}, 직렬화가 값을 한 번 더 감싸지 않게).
 * Lua 는 cjson 으로 server 를 꺼내 비교한다. 예전 값(서버 주소 문자열, JSON 문자열로 저장)도 그 주소를 server 로 본다
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class UserLocationRegistry {

    /**
     * 값에서 server 를 꺼내는 Lua 함수. 새 값 {"server": ...} / 예전 값 "\"ws://...\"" / 예전 값 ws://... (JSON 아님) 모두
     */
    private static final String SERVER_OF = """
        local function server_of(value)
          if not value then return nil end
          local ok, decoded = pcall(cjson.decode, value)
          if not ok then return value end
          if type(decoded) == 'table' then return decoded['server'] end
          return decoded
        end
        """;

    private static final long TTL_SECONDS = ChatPresenceKeys.TTL.toSeconds();

    /**
     * KEYS = [{userId}], ARGV = [지금 값(그대로), 새 값]. 값이 그대로일 때만 새 값을 EX 60 으로 쓴다 (죽은 서버 값 넘겨받기)
     */
    static final RedisScript<Long> COMPARE_AND_SET = new DefaultRedisScript<>(
        "if redis.call('get', KEYS[1]) == ARGV[1] then redis.call('set', KEYS[1], ARGV[2], 'EX', " + TTL_SECONDS + ") "
            + "return 1 end return 0", Long.class);

    /**
     * KEYS = [{userId}], ARGV = [이 서버 "ws://ip:port", 새 값]. server 가 이 서버면 새 값을 EX 60 으로 쓴다.
     * 키가 없으면(TTL 이 지남, Redis failover 로 사라짐) 기기를 가진 이 서버가 다시 적는다 (NX 와 같은 효과).
     * 다른 서버 값이면 건드리지 않는다
     */
    static final RedisScript<Long> SET_IF_OWNER = new DefaultRedisScript<>(SERVER_OF
        + "local value = redis.call('get', KEYS[1]) "
        + "if value == false or server_of(value) == ARGV[1] then "
        + "redis.call('set', KEYS[1], ARGV[2], 'EX', " + TTL_SECONDS + ") return 1 end return 0", Long.class);

    /**
     * KEYS = [{userId}], ARGV = [이 서버 "ws://ip:port"]. server 가 이 서버일 때만 지운다
     */
    static final RedisScript<Long> COMPARE_AND_DELETE = new DefaultRedisScript<>(SERVER_OF
        + "local value = redis.call('get', KEYS[1]) "
        + "if value and server_of(value) == ARGV[1] then return redis.call('del', KEYS[1]) end return 0", Long.class);

    private final StringRedisTemplate stringRedisTemplate;
    private final DiscoveryClient discoveryClient;

    /**
     * 이 유저의 접속 위치를 이 서버로 잡는다. 새로 적을 때는 상태 online, TTL 60초
     *
     * @param chatServer 이 서버 ip:port
     * @return 이 서버로 잡았으면 true, 다른 살아 있는 서버가 갖고 있으면 false
     */
    public boolean claim(String userId, String chatServer) {
        String key = ChatPresenceKeys.userKey(userId);
        String value = ChatPresenceKeys.userKeyValue(chatServer, ChatPresenceKeys.ONLINE);
        if (Boolean.TRUE.equals(stringRedisTemplate.opsForValue().setIfAbsent(key, value, ChatPresenceKeys.TTL))) {
            return true;
        }

        String current = stringRedisTemplate.opsForValue().get(key);
        if (current == null) {
            // 그 사이 지워졌다 / 만료됐다 : 다시 NX
            return Boolean.TRUE.equals(stringRedisTemplate.opsForValue().setIfAbsent(key, value, ChatPresenceKeys.TTL));
        }
        ChatPresence presence = ChatPresenceKeys.parse(current);
        if (presence != null && presence.chatServer().equals(chatServer)) {
            return true;
        }
        if (presence != null && ChatServers.alive(discoveryClient).contains(presence.chatServer())) {
            return false;
        }

        // 죽은 서버 (또는 알아볼 수 없는 값)
        log.info("userId : {} was on dead chat server {}, take over", userId, current);
        Long changed = stringRedisTemplate.execute(COMPARE_AND_SET, List.of(key), current, value);
        return Long.valueOf(1).equals(changed);
    }

    /**
     * 접속 상태를 쓰고 TTL 을 60초로 다시 잡는다. server 가 이 서버(또는 키 없음)일 때만
     *
     * @param status {@link ChatPresenceKeys#ONLINE} | {@link ChatPresenceKeys#AWAY}
     * @return 썼으면 true, 다른 서버로 넘어가서 안 썼으면 false
     */
    public boolean writePresence(String userId, String chatServer, String status) {
        Long written = stringRedisTemplate.execute(SET_IF_OWNER, List.of(ChatPresenceKeys.userKey(userId)),
            ChatPresenceKeys.serverUri(chatServer), ChatPresenceKeys.userKeyValue(chatServer, status));
        return Long.valueOf(1).equals(written);
    }

    /**
     * 이 서버의 마지막 기기가 끊겼을 때. server 가 아직 이 서버일 때만 지운다
     */
    public void release(String userId, String chatServer) {
        stringRedisTemplate.execute(COMPARE_AND_DELETE, List.of(ChatPresenceKeys.userKey(userId)),
            ChatPresenceKeys.serverUri(chatServer));
    }
}
