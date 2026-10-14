package com.distributed.chat.system.common.redis;

import java.time.Duration;

/**
 * 채팅 서버 접속 위치 / 접속 상태 Redis 키 모양. 쓰는 쪽은 채팅 서버 하나뿐이고 나머지는 읽기만 한다
 * <ul>
 *   <li>{@code {userId}} → "ws://{ip:port}" : 그 유저의 기기들이 모여 있는 채팅 서버 (유저 하나 = 서버 하나).
 *       consumer / api-gateway 가 읽는다. 값이 없거나 가리키는 서버가 Eureka 에 없으면(죽은 서버) 오프라인이다 ({@link ChatServers})</li>
 *   <li>{@code presence:{userId}} → "online" | "away", TTL 60초 : 접속 상태. connection-status 가 읽는다.
 *       키가 없으면 offline (채팅 서버가 갑자기 죽어도 TTL 이 지나면 저절로 offline)</li>
 * </ul>
 */
public final class ChatPresenceKeys {

    public static final String WEBSOCKET_SCHEME = "ws://";

    public static final String PRESENCE_KEY_PREFIX = "presence:";

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
     * 접속 상태 키 만료. 채팅 서버는 이보다 짧은 주기로 갱신한다
     */
    public static final Duration PRESENCE_TTL = Duration.ofSeconds(60);

    private ChatPresenceKeys() {
    }

    /**
     * 유저의 접속 위치 키 (키 = userId 그대로)
     */
    public static String userKey(Object userId) {
        return String.valueOf(userId);
    }

    /**
     * {@link #userKey} 의 값 : "ws://{ip:port}" (api-gateway 가 그대로 라우팅 주소로 쓴다)
     */
    public static String userKeyValue(String chatServer) {
        return WEBSOCKET_SCHEME + chatServer;
    }

    /**
     * {@link #userKey} 의 값에서 ip:port 만 꺼낸다
     */
    public static String chatServerOf(String userKeyValue) {
        return userKeyValue.startsWith(WEBSOCKET_SCHEME) ? userKeyValue.substring(WEBSOCKET_SCHEME.length()) : userKeyValue;
    }

    /**
     * 유저의 접속 상태 키 : "presence:{userId}"
     */
    public static String presenceKey(Object userId) {
        return PRESENCE_KEY_PREFIX + userId;
    }

    /**
     * {@link #presenceKey} 의 값(없으면 null)을 응답용 상태로 바꾼다. 모르는 값도 offline
     */
    public static String statusOf(Object presenceValue) {
        return ONLINE.equals(presenceValue) || AWAY.equals(presenceValue) ? (String) presenceValue : OFFLINE;
    }
}
