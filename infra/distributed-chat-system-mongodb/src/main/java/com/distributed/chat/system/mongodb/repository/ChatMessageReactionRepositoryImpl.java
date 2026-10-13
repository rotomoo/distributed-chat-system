package com.distributed.chat.system.mongodb.repository;

import com.distributed.chat.system.mongodb.collection.ChatMessage;
import com.distributed.chat.system.mongodb.collection.ChatMessage.Reaction;
import com.distributed.chat.system.mongodb.config.MongoTemplateConfig;
import java.util.List;
import java.util.Optional;
import org.bson.Document;
import org.bson.types.ObjectId;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.data.mongodb.core.FindAndModifyOptions;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;

/**
 * Querydsl 에는 MongoDB 갱신 DSL($push / $addToSet / $pull / findAndModify)이 없어 MongoTemplate 으로 한다.
 * <p>
 * 각 단계는 문서 하나에 대한 원자적 갱신이다. 단계 사이에 같은 메시지에 대한 다른 갱신이 끼지 않는 것은
 * Kafka 가 보장한다 (chat-reaction 토픽 key = channelId → 한 채널의 리액션은 한 컨슈머 스레드가 차례로 처리)
 */
public class ChatMessageReactionRepositoryImpl implements ChatMessageReactionRepository {

    private static final String REACTIONS = "reactions";
    private static final String EMOJI = "reactions.emoji";
    private static final String MATCHED_USER_IDS = "reactions.$.userIds";

    private final MongoTemplate mongoTemplate;

    public ChatMessageReactionRepositoryImpl(@Qualifier(MongoTemplateConfig.MONGO_TEMPLATE) MongoTemplate mongoTemplate) {
        this.mongoTemplate = mongoTemplate;
    }

    @Override
    public Optional<List<Reaction>> addReaction(ObjectId messageId, Long channelId, String emoji, Long userId) {
        // 1. 이모지가 없고 서로 다른 이모지가 50개 미만이면 빈 항목을 만든다 (reactions.49 가 없다 = 50개 미만)
        mongoTemplate.updateFirst(
            Query.query(messageCriteria(messageId, channelId)
                .and(EMOJI).ne(emoji)
                .and(REACTIONS + "." + (ChatMessage.MAX_REACTION_EMOJIS - 1)).exists(false)),
            new Update().push(REACTIONS, new Document("emoji", emoji).append("userIds", List.of())),
            ChatMessage.class);

        // 2. 그 이모지에 나를 넣는다 (이미 있으면 그대로)
        ChatMessage updated = mongoTemplate.findAndModify(
            Query.query(messageCriteria(messageId, channelId).and(EMOJI).is(emoji)),
            new Update().addToSet(MATCHED_USER_IDS, userId),
            FindAndModifyOptions.options().returnNew(true),
            ChatMessage.class);
        if (updated != null) {
            return Optional.of(reactionsOf(updated));
        }

        // 이모지 50개가 꽉 찼으면 바뀐 게 없다. 메시지가 있으면 지금 상태를 돌려준다
        return Optional.ofNullable(mongoTemplate.findOne(Query.query(messageCriteria(messageId, channelId)), ChatMessage.class))
            .map(ChatMessageReactionRepositoryImpl::reactionsOf);
    }

    @Override
    public Optional<List<Reaction>> removeReaction(ObjectId messageId, Long channelId, String emoji, Long userId) {
        // 1. 그 이모지에서 나를 뺀다
        mongoTemplate.updateFirst(
            Query.query(messageCriteria(messageId, channelId).and(EMOJI).is(emoji)),
            new Update().pull(MATCHED_USER_IDS, userId),
            ChatMessage.class);

        // 2. 누른 사람이 없는 이모지를 지우고 결과를 받는다
        ChatMessage updated = mongoTemplate.findAndModify(
            Query.query(messageCriteria(messageId, channelId)),
            new Update().pull(REACTIONS, new Document("userIds", new Document("$size", 0))),
            FindAndModifyOptions.options().returnNew(true),
            ChatMessage.class);
        return Optional.ofNullable(updated).map(ChatMessageReactionRepositoryImpl::reactionsOf);
    }

    private static Criteria messageCriteria(ObjectId messageId, Long channelId) {
        return Criteria.where("_id").is(messageId).and("channelId").is(channelId);
    }

    private static List<Reaction> reactionsOf(ChatMessage message) {
        return message.getReactions() == null ? List.of() : message.getReactions();
    }
}
