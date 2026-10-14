package com.distributed.chat.system.common.event;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.util.List;

/**
 * 채팅 서버 → 프로듀서 → 컨슈머 : 채팅 메시지 (토픽 chat-send-message, key = channelId). 호환 규칙은 {@link EventJson}
 *
 * @param eventId           채팅 서버가 발행할 때 만든 UUID. 컨슈머가 이 값으로 한 번만 저장한다 (재시도 / 중복 발행에도 문서 1개)
 * @param sendUserId        보낸 사람
 * @param channelId         채널
 * @param message           본문 (첨부만 있으면 빈 문자열)
 * @param publishDt         채팅 서버가 받은 시각 (epoch millis)
 * @param attachmentFileIds 업로드한 파일 ID (GridFS). 이름 / 형식 / 크기는 컨슈머가 GridFS 에서 채운다
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record ChatMessageEvent(
    String eventId,
    long sendUserId,
    long channelId,
    String message,
    long publishDt,
    List<String> attachmentFileIds
) {

    public ChatMessageEvent {
        eventId = EventDefaults.text(eventId);
        message = EventDefaults.text(message);
        attachmentFileIds = EventDefaults.list(attachmentFileIds);
    }

    /**
     * 없는 필드를 모두 기본값으로 둔 빈 메시지 (전달 이벤트에 메시지가 빠졌을 때)
     */
    public static ChatMessageEvent empty() {
        return new ChatMessageEvent(null, 0L, 0L, null, 0L, null);
    }
}
