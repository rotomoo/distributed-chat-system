package com.distributed.chat.system.mongodb.repository;

import com.distributed.chat.system.mongodb.collection.ChatMessage;
import java.util.List;
import org.bson.types.ObjectId;
import org.springframework.data.domain.Pageable;
import org.springframework.data.mongodb.repository.MongoRepository;

public interface ChatMessageRepository extends MongoRepository<ChatMessage, String> {

    /**
     * 채널의 최근 메시지 (최신순). _id(ObjectId) 는 생성 시각 순이라 정렬 기준으로 쓴다
     */
    List<ChatMessage> findByChannelIdOrderByIdDesc(Long channelId, Pageable pageable);

    /**
     * before 보다 이전 메시지 (최신순) — 위로 스크롤 시 더 불러오기.
     * _id 는 ObjectId 로 저장되므로 비교 값도 ObjectId 여야 한다 (문자열로 넘기면 타입이 달라 아무것도 매칭되지 않는다)
     */
    List<ChatMessage> findByChannelIdAndIdLessThanOrderByIdDesc(Long channelId, ObjectId before, Pageable pageable);
}
