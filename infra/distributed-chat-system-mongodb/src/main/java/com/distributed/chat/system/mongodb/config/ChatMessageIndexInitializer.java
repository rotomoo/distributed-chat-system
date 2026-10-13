package com.distributed.chat.system.mongodb.config;

import com.distributed.chat.system.mongodb.collection.ChatMessage;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.data.domain.Sort.Direction;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.index.Index;
import org.springframework.stereotype.Component;

/**
 * 채널별 메시지 조회(channelId = ? order by _id desc) 인덱스와 eventId 유니크 인덱스. 이미 있으면 아무 일도 하지 않는다
 */
@Component
public class ChatMessageIndexInitializer {

    private final MongoTemplate mongoTemplate;

    public ChatMessageIndexInitializer(@Qualifier(MongoTemplateConfig.MONGO_TEMPLATE) MongoTemplate mongoTemplate) {
        this.mongoTemplate = mongoTemplate;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void ensureIndexes() {
        mongoTemplate.indexOps(ChatMessage.class)
            .ensureIndex(new Index().on("channelId", Direction.ASC).on("_id", Direction.DESC).named("ix_channel_id_id"));
        // 같은 이벤트는 한 번만 저장 (eventId 가 없는 예전 문서는 sparse 로 제외)
        mongoTemplate.indexOps(ChatMessage.class)
            .ensureIndex(new Index().on("eventId", Direction.ASC).unique().sparse().named("ux_event_id"));
    }
}
