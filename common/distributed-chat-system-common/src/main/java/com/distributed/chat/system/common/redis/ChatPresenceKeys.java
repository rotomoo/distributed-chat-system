package com.distributed.chat.system.common.redis;

/**
 * 채팅 서버 접속 위치 Redis 키 모양. 쓰는 쪽은 채팅 서버 하나뿐이고 consumer / client-api / api-gateway 는 읽기만 한다
 * <ul>
 *   <li>{@code {userId}} → "ws://{ip:port}" : 그 유저의 기기들이 모여 있는 채팅 서버 (유저 하나 = 서버 하나)</li>
 * </ul>
 * 값이 없거나 가리키는 서버가 Eureka 에 없으면(죽은 서버) 오프라인이다 ({@link ChatServers})
 */
public final class ChatPresenceKeys {

    public static final String WEBSOCKET_SCHEME = "ws://";

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
}
