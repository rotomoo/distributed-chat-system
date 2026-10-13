package com.distributed.chat.system.mongodb.repository;

import java.util.Map;
import org.bson.types.ObjectId;

/**
 * 채널 / DM 목록의 안 읽은 수를 여러 채널 한 번에 센다 (채널마다 쿼리하지 않음)
 */
public interface ChatMessageUnreadCountRepository {

    /**
     * @param unreadAfterByChannel 채널 ID → 이 메시지 ID 보다 뒤가 안 읽은 것 (null 이면 채널 전체)
     * @param readerUserId         읽는 사람 (내가 보낸 메시지는 세지 않는다)
     * @param cap                  채널마다 이 수까지만 센다 (0 이면 끝까지)
     * @return 채널 ID → 안 읽은 수. 0 인 채널은 빠진다
     */
    Map<Long, Integer> countUnreadByChannel(Map<Long, ObjectId> unreadAfterByChannel, Long readerUserId, int cap);
}
