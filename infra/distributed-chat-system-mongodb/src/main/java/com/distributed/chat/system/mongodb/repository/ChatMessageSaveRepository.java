package com.distributed.chat.system.mongodb.repository;

import com.distributed.chat.system.mongodb.collection.ChatMessage;
import java.util.Optional;

/**
 * eventId 로 한 번만 저장한다 (Kafka 재시도 / 중복 발행에도 문서 하나, 같은 messageId)
 */
public interface ChatMessageSaveRepository {

    /**
     * eventId 가 같은 문서가 없으면 넣고, 있으면 그대로 둔다. 어느 쪽이든 저장된 문서를 돌려준다
     *
     * @param message eventId 가 있어야 하고 id 는 비어 있어야 한다 (MongoDB 가 삽입할 때 만든다)
     */
    ChatMessage saveOnce(ChatMessage message);

    Optional<ChatMessage> findByEventId(String eventId);
}
