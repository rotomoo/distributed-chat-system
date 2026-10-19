package com.distributed.chat.system.common.event;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.util.List;

/**
 * 컨슈머 → kafka-producer(HTTP) → 알림 서버 : 오프라인 멤버(접속 위치 없음 / 죽은 서버)에게 웹 푸시 (토픽 chat-notification, key = channelId).
 * 보낸 사람은 빠진다. 받는 사람이 많으면 500명씩 나눠 여러 건으로 보낸다. 리액션은 알림을 만들지 않는다. 호환 규칙은 {@link EventJson}
 *
 * @param messageId       저장된 메시지 ID
 * @param channelId       채널
 * @param senderId        보낸 사람
 * @param preview         본문 앞 100자 (코드 포인트 기준, 이모지가 잘리지 않게)
 * @param attachmentCount 확인된 첨부 수 (본문 없이 첨부만이면 알림 본문이 "파일 N개")
 * @param publishDt       채팅 서버가 받은 시각 (epoch millis)
 * @param receiverUserIds 받을 사람 (최대 500명)
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record ChatNotificationEvent(
    String messageId,
    long channelId,
    long senderId,
    String preview,
    int attachmentCount,
    long publishDt,
    List<Long> receiverUserIds
) {

    public ChatNotificationEvent {
        messageId = EventDefaults.text(messageId);
        preview = EventDefaults.text(preview);
        receiverUserIds = EventDefaults.list(receiverUserIds);
    }
}
