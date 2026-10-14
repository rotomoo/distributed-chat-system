package com.distributed.chat.system.common.event;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/**
 * 채팅 서버 → 프로듀서 → 컨슈머 : 이모지 리액션 추가 / 제거 (토픽 chat-reaction, key = channelId). 호환 규칙은 {@link EventJson}.
 * 갱신이 $addToSet / $pull 이라 같은 이벤트를 다시 처리해도 결과가 같아서 메시지처럼 eventId 가 필요 없다
 *
 * @param sendUserId 누른 사람
 * @param channelId  채널
 * @param messageId  대상 메시지 ID (ObjectId 24자리 hex)
 * @param emoji      이모지
 * @param action     추가 / 제거. 없거나 모르는 값이면 null → 컨슈머가 버린다 (예전 ACTION_UNSPECIFIED 와 같다)
 * @param publishDt  채팅 서버가 받은 시각 (epoch millis)
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record ChatReactionEvent(
    long sendUserId,
    long channelId,
    String messageId,
    String emoji,
    @JsonFormat(with = JsonFormat.Feature.READ_UNKNOWN_ENUM_VALUES_AS_NULL) Action action,
    long publishDt
) {

    public enum Action {
        ADD,
        REMOVE
    }

    public ChatReactionEvent {
        messageId = EventDefaults.text(messageId);
        emoji = EventDefaults.text(emoji);
    }
}
