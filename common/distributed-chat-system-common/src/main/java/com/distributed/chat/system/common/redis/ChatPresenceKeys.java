package com.distributed.chat.system.common.redis;

/**
 * 채팅 서버 접속 정보 Redis 키 모양 (채팅 서버가 쓰고 client-api / 컨슈머 / service-discovery 가 읽는다)
 * <ul>
 *   <li>{@code {userId}} → "ws://{ip:port}" : 그 유저가 마지막으로 붙은 채팅 서버 (api-gateway 라우팅용)</li>
 *   <li>{@code chatServerUri:{ip:port}} → { userId, ... } : 그 채팅 서버에 지금 연결된 유저 Set</li>
 * </ul>
 */
public final class ChatPresenceKeys {

    public static final String CHAT_SERVER_KEY_PREFIX = "chatServerUri:";
    public static final String WEBSOCKET_SCHEME = "ws://";

    private ChatPresenceKeys() {
    }

    /**
     * 유저가 마지막으로 붙은 채팅 서버 키 (키 = userId 그대로)
     */
    public static String userKey(Object userId) {
        return String.valueOf(userId);
    }

    /**
     * @param chatServer ip:port
     */
    public static String chatServerKey(String chatServer) {
        return CHAT_SERVER_KEY_PREFIX + chatServer;
    }

    /**
     * {@link #userKey} 의 값 : "ws://{ip:port}"
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
