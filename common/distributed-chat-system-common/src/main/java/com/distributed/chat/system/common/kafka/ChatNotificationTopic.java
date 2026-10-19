package com.distributed.chat.system.common.kafka;

/**
 * 알림 이벤트 토픽 ({@link com.distributed.chat.system.common.event.ChatNotificationEvent} JSON). 발행은 kafka-producer (message-consumer 가 HTTP 로 요청), 읽는 쪽은 notification-consumer 하나뿐이다
 * <ul>
 *   <li>key = channelId (같은 채널 알림은 한 파티션에서 차례로)</li>
 *   <li>한 건에 받는 사람은 {@link #MAX_RECEIVERS} 명까지. 넘으면 여러 건으로 나눈다</li>
 * </ul>
 */
public final class ChatNotificationTopic {

    public static final String TOPIC = "chat-notification";

    /**
     * 이벤트 한 건의 receiver_user_ids 최대 수 (채팅 서버 전달 묶음과 같은 500)
     */
    public static final int MAX_RECEIVERS = 500;

    /**
     * 미리보기 최대 길이 (코드 포인트)
     */
    public static final int PREVIEW_MAX_CODE_POINTS = 100;

    private ChatNotificationTopic() {
    }

    /**
     * 본문 앞 {@link #PREVIEW_MAX_CODE_POINTS} 자. 서로게이트 쌍(이모지)을 반으로 자르지 않는다
     */
    public static String preview(String message) {
        if (message == null) {
            return "";
        }
        String trimmed = message.strip();
        if (trimmed.codePointCount(0, trimmed.length()) <= PREVIEW_MAX_CODE_POINTS) {
            return trimmed;
        }
        return trimmed.substring(0, trimmed.offsetByCodePoints(0, PREVIEW_MAX_CODE_POINTS));
    }
}
