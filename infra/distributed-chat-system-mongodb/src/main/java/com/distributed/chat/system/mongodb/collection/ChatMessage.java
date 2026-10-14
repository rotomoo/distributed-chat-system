package com.distributed.chat.system.mongodb.collection;

import java.time.Instant;
import com.querydsl.core.annotations.QueryTransient;
import java.util.List;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.ToString;
import lombok.experimental.Accessors;
import org.bson.types.ObjectId;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Builder
@Document("chatMessage")
public class ChatMessage {

    /**
     * 한 메시지에 달 수 있는 서로 다른 이모지 수
     */
    public static final int MAX_REACTION_EMOJIS = 50;

    /**
     * 메시지 ID (ObjectId, 생성 시각 순). 밖(API / 웹소켓 / 서버간 이벤트)으로는 toHexString() 24자리 문자열로 나간다.
     * <p>
     * 채팅 서버가 아니라 저장할 때(컨슈머의 upsert 삽입 시 MongoDB 가) 만든다. 한 채널의 메시지는 Kafka 파티션 순서대로 저장되므로
     * _id 순서 = 채널 안 순서가 되어 커서(before / after / 읽음 위치)로 쓸 수 있다. 여러 채팅 서버가 발행할 때 만들면 서버마다
     * 시각 / 순서가 섞여 커서가 어긋난다. (재시도 토픽으로 늦게 저장된 메시지는 그만큼 뒤에 놓인다)
     */
    @Id
    private ObjectId id;

    /**
     * 채팅 서버가 발행할 때 만든 UUID (유니크 인덱스). 같은 이벤트를 여러 번 받아도 문서는 하나다
     */
    private String eventId;
    private Long channelId;
    private String content;
    private Instant eventPublishDt;
    private Instant createDt;
    private Instant updateDt;
    private Instant deleteDt;
    private Long createUserId;

    /**
     * 첨부파일 (컨슈머가 GridFS metadata 로 확인한 것만). 없으면 저장하지 않는다 (null).
     * 조회 조건으로 쓰지 않으므로 QClass 에서 뺀다
     */
    @QueryTransient
    private List<Attachment> attachments;

    /**
     * 이모지 리액션. 개수는 userIds 길이 (따로 저장하지 않음). 없으면 null.
     * 갱신은 MongoTemplate 원자적 연산으로만 하므로 QClass 에서 뺀다
     */
    @QueryTransient
    private List<Reaction> reactions;

    /**
     * 첨부파일 하나. querydsl-apt 5.0 이 record 를 처리하지 못해 불변 클래스로 둔다 (접근자는 record 처럼 fileId())
     */
    @Getter
    @Accessors(fluent = true)
    @AllArgsConstructor
    @EqualsAndHashCode
    @ToString
    public static class Attachment {

        /**
         * GridFS 파일 ID
         */
        private final String fileId;
        private final String name;
        private final String contentType;
        private final long size;
    }

    /**
     * 이모지 하나와 누른 사람들
     */
    @Getter
    @Accessors(fluent = true)
    @AllArgsConstructor
    @EqualsAndHashCode
    @ToString
    public static class Reaction {

        private final String emoji;
        private final List<Long> userIds;
    }
}
