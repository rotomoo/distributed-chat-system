package com.distributed.chat.system.mongodb.repository.querydsl;

import com.distributed.chat.system.mongodb.collection.ChatMessage;
import java.util.List;
import java.util.Optional;
import org.bson.types.ObjectId;
import org.springframework.data.domain.Slice;

/**
 * chatMessage 조회 (Querydsl). 커서는 _id (ObjectId, 생성 시각 순) — 인덱스 (channelId, _id) 를 탄다.
 * <p>
 * 안 읽은 메시지 = lastRead(안 읽은 기준) 보다 뒤(_id) 이면서 내가 보내지 않은 메시지. 기준은 호출하는 쪽이 정한다
 * (마지막으로 읽은 메시지와 채널에 들어온 시각 중 뒤의 것). null 이면 채널 전체
 */
public interface ChatMessageQueryDsl {

    /**
     * 채널의 최근 메시지 size 개 (최신순). hasNext = 이보다 더 이전 메시지가 있다
     */
    Slice<ChatMessage> findLatestMessages(Long channelId, int size);

    /**
     * before 보다 이전 메시지 size 개 (최신순) — 위로 스크롤 시 더 불러오기. hasNext = 이보다 더 이전 메시지가 있다
     */
    Slice<ChatMessage> findMessagesBefore(Long channelId, ObjectId before, int size);

    /**
     * after 보다 뒤의 메시지 size 개 (오래된 순) — 재접속 시 빈틈 채우기. hasNext = 이보다 더 최신 메시지가 있다
     */
    Slice<ChatMessage> findMessagesAfter(Long channelId, ObjectId after, int size);

    /**
     * 그 채널에 있는 메시지인지 (읽음 위치로 쓸 수 있는지). _id 로 한 건만 본다
     */
    boolean existsInChannel(ObjectId messageId, Long channelId);

    /**
     * 안 읽은 메시지 수
     *
     * @param lastRead 마지막으로 읽은 메시지 ID (없으면 null)
     * @param limit    이 수까지만 센다 (0 이면 끝까지)
     */
    long countUnread(Long channelId, ObjectId lastRead, Long readerUserId, int limit);

    /**
     * 안 읽은 첫 메시지 (화면의 "여기부터 안 읽음" 구분선 위치)
     *
     * @param lastRead 마지막으로 읽은 메시지 ID (없으면 null)
     */
    Optional<ChatMessage> findFirstUnread(Long channelId, ObjectId lastRead, Long readerUserId);
}
