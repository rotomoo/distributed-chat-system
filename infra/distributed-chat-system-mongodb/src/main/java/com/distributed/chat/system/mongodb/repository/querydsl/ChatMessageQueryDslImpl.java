package com.distributed.chat.system.mongodb.repository.querydsl;

import static com.distributed.chat.system.mongodb.collection.QChatMessage.chatMessage;

import com.distributed.chat.system.mongodb.collection.ChatMessage;
import com.distributed.chat.system.mongodb.config.MongoTemplateConfig;
import com.querydsl.core.BooleanBuilder;
import com.querydsl.core.types.Predicate;
import com.querydsl.core.types.dsl.ComparablePath;
import com.querydsl.core.types.dsl.Expressions;
import java.util.List;
import java.util.Optional;
import org.bson.types.ObjectId;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Slice;
import org.springframework.data.domain.SliceImpl;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.BasicQuery;
import org.springframework.data.mongodb.repository.support.SpringDataMongodbQuery;

/**
 * 커서(_id)는 ObjectId 로 비교 / 정렬한다
 */
public class ChatMessageQueryDslImpl implements ChatMessageQueryDsl {

    /**
     * QChatMessage.id 와 같은 경로를 비교 가능한 타입으로 본 것. querydsl-apt 는 ObjectId 를 QObjectId(값 비교 eq 만, lt / gt / 정렬 없음)로
     * 만들고 @QueryType(COMPARABLE) 도 먹지 않아서, 생성된 경로의 metadata 를 그대로 써서 ComparablePath 로 감싼다
     */
    static final ComparablePath<ObjectId> ID = Expressions.comparablePath(ObjectId.class, chatMessage.id.getMetadata());

    private final MongoTemplate mongoTemplate;

    public ChatMessageQueryDslImpl(@Qualifier(MongoTemplateConfig.MONGO_TEMPLATE) MongoTemplate mongoTemplate) {
        this.mongoTemplate = mongoTemplate;
    }

    @Override
    public Slice<ChatMessage> findLatestMessages(Long channelId, int size) {
        return slice(query()
            .where(chatMessage.channelId.eq(channelId))
            .orderBy(ID.desc()), size);
    }

    @Override
    public Slice<ChatMessage> findMessagesBefore(Long channelId, ObjectId before, int size) {
        return slice(query()
            .where(chatMessage.channelId.eq(channelId), ID.lt(before))
            .orderBy(ID.desc()), size);
    }

    @Override
    public Slice<ChatMessage> findMessagesAfter(Long channelId, ObjectId after, int size) {
        return slice(query()
            .where(chatMessage.channelId.eq(channelId), ID.gt(after))
            .orderBy(ID.asc()), size);
    }

    /**
     * size + 1 개를 가져와서 하나 더 있으면 다음이 있다 (count 쿼리 없이). 순서는 query 의 정렬 그대로
     */
    static Slice<ChatMessage> slice(SpringDataMongodbQuery<ChatMessage> query, int size) {
        List<ChatMessage> fetched = query.limit(size + 1L).fetch();
        boolean hasNext = fetched.size() > size;
        return new SliceImpl<>(hasNext ? fetched.subList(0, size) : fetched, PageRequest.of(0, size), hasNext);
    }

    @Override
    public boolean existsInChannel(ObjectId messageId, Long channelId) {
        return mongoTemplate.exists(
            new BasicQuery(query().where(ID.eq(messageId), chatMessage.channelId.eq(channelId)).asDocument()), ChatMessage.class);
    }

    /**
     * SpringDataMongodbQuery.fetchCount 는 limit 을 지우고 끝까지 세므로, 조건만 Querydsl 로 만들고
     * 세는 건 limit 을 그대로 쓰는 MongoTemplate.count 로 한다 (limit 개를 세면 멈춤)
     */
    @Override
    public long countUnread(Long channelId, ObjectId lastRead, Long readerUserId, int limit) {
        BasicQuery unread = new BasicQuery(query().where(unreadPredicate(channelId, lastRead, readerUserId)).asDocument());
        return mongoTemplate.count(unread.limit(limit), ChatMessage.class);
    }

    @Override
    public Optional<ChatMessage> findFirstUnread(Long channelId, ObjectId lastRead, Long readerUserId) {
        return Optional.ofNullable(query()
            .where(unreadPredicate(channelId, lastRead, readerUserId))
            .orderBy(ID.asc())
            .fetchFirst());
    }

    private static Predicate unreadPredicate(Long channelId, ObjectId lastRead, Long readerUserId) {
        BooleanBuilder unread = new BooleanBuilder(chatMessage.channelId.eq(channelId));
        if (lastRead != null) {
            unread.and(ID.gt(lastRead));
        }
        return unread.and(chatMessage.createUserId.ne(readerUserId));
    }

    private SpringDataMongodbQuery<ChatMessage> query() {
        return new SpringDataMongodbQuery<>(mongoTemplate, ChatMessage.class);
    }
}
