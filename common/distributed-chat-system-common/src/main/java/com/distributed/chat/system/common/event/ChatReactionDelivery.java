package com.distributed.chat.system.common.event;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.util.List;

/**
 * 컨슈머 → 채팅 서버 (POST /internal/chat/deliver-reaction) : 바뀐 뒤의 그 메시지 리액션 전체 (클라이언트는 통째로 교체).
 * 호환 규칙은 {@link EventJson}
 *
 * @param messageId      메시지 ID (ObjectId 24자리 hex)
 * @param channelId      채널
 * @param reactions      바뀐 뒤의 리액션 전체
 * @param receiveUserIds 이 채팅 서버에서 받을 사람
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record ChatReactionDelivery(
    String messageId,
    long channelId,
    List<Reaction> reactions,
    List<Long> receiveUserIds
) {

    public ChatReactionDelivery {
        messageId = EventDefaults.text(messageId);
        reactions = EventDefaults.list(reactions);
        receiveUserIds = EventDefaults.list(receiveUserIds);
    }

    /**
     * 메시지의 이모지 하나와 누른 사람들 (개수 = userIds 길이)
     */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Reaction(String emoji, List<Long> userIds) {

        public Reaction {
            emoji = EventDefaults.text(emoji);
            userIds = EventDefaults.list(userIds);
        }
    }
}
