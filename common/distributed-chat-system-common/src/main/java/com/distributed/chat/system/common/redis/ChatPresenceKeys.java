package com.distributed.chat.system.common.redis;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.json.JsonMapper;
import java.time.Duration;
import java.util.Map;

/**
 * 채팅 서버 접속 정보 Redis 키 모양. 유저당 키 하나에 "어느 서버에 붙었나" 와 "접속 상태" 를 함께 둔다.
 * 쓰는 쪽은 채팅 서버 하나뿐이고 consumer / api-gateway / connection-status 는 읽기만 한다
 * <ul>
 *   <li>{@code {userId}} → {@code {"server":"ws://ip:port","status":"online"|"away"}}, TTL 60초</li>
 *   <li>server : 그 유저의 기기들이 모여 있는 채팅 서버 (유저 하나 = 서버 하나). consumer 가 전달 대상을, api-gateway 가 라우팅을 정한다</li>
 *   <li>status : 기기 하나라도 active 면 online, 모두 away 면 away. connection-status 가 읽는다</li>
 *   <li>키가 없으면 offline. 채팅 서버가 갑자기 죽어도 TTL 이 지나면 위치와 상태가 함께 사라진다.
 *       TTL 전이라도 server 가 Eureka 에 없으면(죽은 서버) consumer / api-gateway 는 오프라인으로 본다 ({@link ChatServers})</li>
 * </ul>
 * 값은 JSON 텍스트 그대로 저장된다 (채팅 서버가 문자열 직렬화로 쓴다). 읽는 쪽 RedisTemplate 의 JSON 직렬화로 읽으면 Map 이 오므로
 * {@link #parse(Object)} 하나로 어느 쪽으로 읽어도 같은 결과를 낸다.
 * <p>
 * <b>예전 값 호환 (한 번의 배포 동안)</b> : 예전 채팅 서버는 {@code {userId}} 에 서버 주소 문자열("ws://ip:port", JSON 문자열로 저장)만 썼다.
 * 이 값은 {server, status: online} 으로 읽는다
 */
public final class ChatPresenceKeys {

    public static final String WEBSOCKET_SCHEME = "ws://";

    /**
     * 기기 하나라도 active
     */
    public static final String ONLINE = "online";

    /**
     * 모든 기기가 away (입력 없음)
     */
    public static final String AWAY = "away";

    /**
     * 키 없음. Redis 에는 쓰지 않고 읽는 쪽이 돌려줄 때만 쓴다
     */
    public static final String OFFLINE = "offline";

    /**
     * 접속 정보 키 만료. 채팅 서버는 이보다 짧은 주기로 다시 쓴다
     */
    public static final Duration TTL = Duration.ofSeconds(60);

    private static final ObjectMapper MAPPER = JsonMapper.builder().build();

    private ChatPresenceKeys() {
    }

    /**
     * 유저의 접속 정보 키 (키 = userId 그대로)
     */
    public static String userKey(Object userId) {
        return String.valueOf(userId);
    }

    /**
     * {@link #userKey} 의 값 (JSON 텍스트)
     *
     * @param chatServer 채팅 서버 ip:port
     * @param status     {@link #ONLINE} | {@link #AWAY}
     */
    public static String userKeyValue(String chatServer, String status) {
        try {
            return MAPPER.writeValueAsString(new ChatPresence(serverUri(chatServer), status));
        } catch (JsonProcessingException e) {
            // 문자열 두 개뿐이라 일어나지 않는다
            throw new IllegalStateException("chat presence serialize fail", e);
        }
    }

    /**
     * 채팅 서버 ip:port → "ws://ip:port" (api-gateway 가 그대로 라우팅 주소로 쓴다)
     */
    public static String serverUri(String chatServer) {
        return WEBSOCKET_SCHEME + chatServer;
    }

    /**
     * "ws://ip:port" 에서 ip:port 만 꺼낸다
     */
    public static String chatServerOf(String serverUri) {
        return serverUri.startsWith(WEBSOCKET_SCHEME) ? serverUri.substring(WEBSOCKET_SCHEME.length()) : serverUri;
    }

    /**
     * {@link #userKey} 의 값을 읽는다. 키가 없거나 알아볼 수 없는 값이면 null (= offline)
     *
     * @param value 문자열 직렬화로 읽은 JSON 텍스트, JSON 직렬화 RedisTemplate 로 읽은 Map, 또는 예전 서버 주소 문자열
     */
    public static ChatPresence parse(Object value) {
        if (value instanceof Map<?, ?> map) {
            return of(map.get("server"), map.get("status"));
        }
        if (!(value instanceof String text) || text.isBlank()) {
            return null;
        }

        String trimmed = text.trim();
        if (!trimmed.startsWith("{") && !trimmed.startsWith("\"")) {
            // 예전 값을 문자열 그대로 읽은 경우 : ws://ip:port
            return legacy(trimmed);
        }
        try {
            Object decoded = MAPPER.readValue(trimmed, Object.class);
            if (decoded instanceof Map<?, ?> map) {
                return of(map.get("server"), map.get("status"));
            }
            // 예전 값을 문자열 직렬화로 읽은 경우 : "ws://ip:port" (JSON 문자열)
            return decoded instanceof String serverUri ? legacy(serverUri) : null;
        } catch (JsonProcessingException e) {
            return null;
        }
    }

    /**
     * 응답용 상태. 키가 없거나 알아볼 수 없으면 offline
     */
    public static String statusOf(Object value) {
        ChatPresence presence = parse(value);
        return presence == null ? OFFLINE : presence.status();
    }

    /**
     * 예전 값 : 서버 주소만 ("ws://" 로 시작해야 한다)
     */
    private static ChatPresence legacy(String serverUri) {
        return serverUri.startsWith(WEBSOCKET_SCHEME) ? of(serverUri, null) : null;
    }

    /**
     * server 가 없으면 null. status 가 없거나 모르는 값이면 online (키가 있다 = 연결된 기기가 있다)
     */
    private static ChatPresence of(Object server, Object status) {
        if (!(server instanceof String serverUri) || serverUri.isBlank()) {
            return null;
        }
        return new ChatPresence(serverUri, AWAY.equals(status) ? AWAY : ONLINE);
    }

    /**
     * {@link #userKey} 의 값
     *
     * @param server "ws://ip:port"
     * @param status {@link #ONLINE} | {@link #AWAY}
     */
    public record ChatPresence(String server, String status) {

        /**
         * ip:port (Eureka 의 chatting 인스턴스 주소와 같은 모양)
         */
        public String chatServer() {
            return chatServerOf(server);
        }
    }
}
