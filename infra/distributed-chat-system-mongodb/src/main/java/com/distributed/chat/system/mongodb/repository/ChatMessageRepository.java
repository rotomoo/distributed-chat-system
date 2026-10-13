package com.distributed.chat.system.mongodb.repository;

import com.distributed.chat.system.mongodb.collection.ChatMessage;
import com.distributed.chat.system.mongodb.repository.querydsl.ChatMessageQueryDsl;
import org.bson.types.ObjectId;
import org.springframework.data.mongodb.repository.MongoRepository;

/**
 * 조회는 Querydsl (ChatMessageQueryDsl), 리액션 갱신은 MongoTemplate 원자적 연산 (ChatMessageReactionRepository),
 * 여러 채널 안 읽은 수는 MongoTemplate 집계 (ChatMessageUnreadCountRepository), 저장은 eventId upsert (ChatMessageSaveRepository)
 */
public interface ChatMessageRepository extends MongoRepository<ChatMessage, ObjectId>, ChatMessageQueryDsl,
    ChatMessageReactionRepository, ChatMessageUnreadCountRepository, ChatMessageSaveRepository {
}
