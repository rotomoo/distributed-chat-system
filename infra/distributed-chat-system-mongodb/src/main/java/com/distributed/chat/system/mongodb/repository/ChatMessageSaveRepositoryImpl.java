package com.distributed.chat.system.mongodb.repository;

import com.distributed.chat.system.mongodb.collection.ChatMessage;
import com.distributed.chat.system.mongodb.config.MongoTemplateConfig;
import java.util.Optional;
import org.bson.Document;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.data.mongodb.core.FindAndModifyOptions;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;

/**
 * Querydsl 에는 upsert / findAndModify 가 없어 MongoTemplate 으로 한다.
 * <p>
 * findAndModify(upsert, $setOnInsert, returnNew) 한 번으로 "없으면 넣고 결과 받기" 를 원자적으로 한다.
 * 동시에 같은 eventId 가 들어와도 유니크 인덱스(ux_event_id)가 둘째를 막고 MongoDB 가 그 upsert 를 다시 시도해 같은 문서를 돌려준다
 */
public class ChatMessageSaveRepositoryImpl implements ChatMessageSaveRepository {

    private final MongoTemplate mongoTemplate;

    public ChatMessageSaveRepositoryImpl(@Qualifier(MongoTemplateConfig.MONGO_TEMPLATE) MongoTemplate mongoTemplate) {
        this.mongoTemplate = mongoTemplate;
    }

    @Override
    public ChatMessage saveOnce(ChatMessage message) {
        if (message.getEventId() == null || message.getEventId().isBlank()) {
            throw new IllegalArgumentException("eventId is required");
        }

        // save() 와 같은 모양(필드 이름 / _class)으로 바꾼 뒤 처음 넣을 때만 쓴다
        Document document = new Document();
        mongoTemplate.getConverter().write(message, document);
        document.remove("_id");
        Update update = new Update();
        document.forEach(update::setOnInsert);

        return mongoTemplate.findAndModify(
            Query.query(Criteria.where("eventId").is(message.getEventId())),
            update,
            FindAndModifyOptions.options().upsert(true).returnNew(true),
            ChatMessage.class);
    }

    @Override
    public Optional<ChatMessage> findByEventId(String eventId) {
        return Optional.ofNullable(mongoTemplate.findOne(Query.query(Criteria.where("eventId").is(eventId)), ChatMessage.class));
    }
}
