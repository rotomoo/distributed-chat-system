package com.distributed.chat.system.mongodb.repository;

import com.distributed.chat.system.mongodb.collection.ChatMessage;
import com.distributed.chat.system.mongodb.config.MongoTemplateConfig;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import org.bson.Document;
import org.bson.types.ObjectId;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.data.mongodb.core.MongoTemplate;

/**
 * Querydsl 에는 MongoDB 집계($unionWith / $limit / $group)가 없어 MongoTemplate 으로 한다.
 * <p>
 * 채널마다 [$match {channelId, _id > 기준, createUserId != 나} → $limit cap → $group] 를 만들고 $unionWith 로 이어서 한 번에 보낸다.
 * <ul>
 *   <li>각 채널 조건이 인덱스 (channelId, _id) 범위를 그대로 탄다 ($or 하나로 묶는 것과 같은 왕복 1번)</li>
 *   <li>$limit 이 채널마다 걸려서 안 읽은 메시지가 아무리 많아도 채널당 cap 개까지만 본다 ($group 하나로 세면 끝까지 세야 함)</li>
 * </ul>
 */
public class ChatMessageUnreadCountRepositoryImpl implements ChatMessageUnreadCountRepository {

    private final MongoTemplate mongoTemplate;

    public ChatMessageUnreadCountRepositoryImpl(@Qualifier(MongoTemplateConfig.MONGO_TEMPLATE) MongoTemplate mongoTemplate) {
        this.mongoTemplate = mongoTemplate;
    }

    @Override
    public Map<Long, Integer> countUnreadByChannel(Map<Long, ObjectId> unreadAfterByChannel, Long readerUserId, int cap) {
        if (unreadAfterByChannel.isEmpty()) {
            return Map.of();
        }
        String collection = mongoTemplate.getCollectionName(ChatMessage.class);

        Iterator<Map.Entry<Long, ObjectId>> channels = unreadAfterByChannel.entrySet().iterator();
        Map.Entry<Long, ObjectId> first = channels.next();
        List<Document> pipeline = new ArrayList<>(channelPipeline(first.getKey(), first.getValue(), readerUserId, cap));
        while (channels.hasNext()) {
            Map.Entry<Long, ObjectId> channel = channels.next();
            pipeline.add(new Document("$unionWith", new Document("coll", collection)
                .append("pipeline", channelPipeline(channel.getKey(), channel.getValue(), readerUserId, cap))));
        }

        Map<Long, Integer> unreadCounts = new HashMap<>();
        for (Document result : mongoTemplate.getCollection(collection).aggregate(pipeline)) {
            unreadCounts.put(((Number) result.get("_id")).longValue(), ((Number) result.get("count")).intValue());
        }
        return unreadCounts;
    }

    private static List<Document> channelPipeline(Long channelId, ObjectId unreadAfter, Long readerUserId, int cap) {
        Document match = new Document("channelId", channelId);
        if (unreadAfter != null) {
            match.append("_id", new Document("$gt", unreadAfter));
        }
        match.append("createUserId", new Document("$ne", readerUserId));
        Document group = new Document("$group", new Document("_id", "$channelId").append("count", new Document("$sum", 1)));
        return cap > 0
            ? List.of(new Document("$match", match), new Document("$limit", cap), group)
            : List.of(new Document("$match", match), group);
    }
}
