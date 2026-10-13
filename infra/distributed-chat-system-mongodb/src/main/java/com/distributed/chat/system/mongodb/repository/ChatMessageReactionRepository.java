package com.distributed.chat.system.mongodb.repository;

import com.distributed.chat.system.mongodb.collection.ChatMessage.Reaction;
import java.util.List;
import java.util.Optional;
import org.bson.types.ObjectId;

/**
 * 메시지 문서 안의 reactions = [{emoji, userIds}] 를 원자적으로 바꾼다.
 * 결과는 바뀐 뒤의 그 메시지 reactions 전체. 메시지가 없거나 channelId 가 다르면 empty
 */
public interface ChatMessageReactionRepository {

    /**
     * 같은 사람이 두 번 눌러도 1명 (멱등). 새 이모지는 서로 다른 이모지가 50개 미만일 때만 생긴다
     */
    Optional<List<Reaction>> addReaction(ObjectId messageId, Long channelId, String emoji, Long userId);

    /**
     * 없는 걸 빼도 그대로 (멱등). 누른 사람이 0명이 된 이모지는 지운다
     */
    Optional<List<Reaction>> removeReaction(ObjectId messageId, Long channelId, String emoji, Long userId);
}
