package com.distributed.chat.system.common.redis;

/**
 * 알림 서버의 Redis 키 모양. 쓰는 쪽도 읽는 쪽도 notification-consumer 하나뿐이다
 * <ul>
 *   <li>{@code notify:throttle:{userId}:{channelId}} : 같은 (유저, 채널) 웹 푸시는 {@link #THROTTLE_SECONDS} 초에 한 번 (SET NX EX)</li>
 * </ul>
 */
public final class NotificationKeys {

    public static final long THROTTLE_SECONDS = 30;

    private NotificationKeys() {
    }

    public static String throttleKey(Long userId, Long channelId) {
        return "notify:throttle:" + userId + ":" + channelId;
    }
}
