package com.distributed.chat.system.mysql.repository;

import com.distributed.chat.system.mysql.entity.ChannelUser;
import com.distributed.chat.system.mysql.pk.ChannelUserPk;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ChannelUserRepository extends JpaRepository<ChannelUser, ChannelUserPk> {

    List<ChannelUser> findAllByUserIdAndDeleteYnIsFalse(Long userId);

    List<ChannelUser> findAllByChannelIdAndDeleteYnIsFalse(Long channelId);

    List<ChannelUser> findAllByChannelIdInAndDeleteYnIsFalse(Collection<Long> channelIds);

    Optional<ChannelUser> findByChannelIdAndUserIdAndDeleteYnIsFalse(Long channelId, Long userId);

    boolean existsByChannelIdAndUserIdAndDeleteYnIsFalse(Long channelId, Long userId);

    /**
     * 채널 멤버 ID 만 (메시지 전달 대상). PK (channel_id, user_id) 의 앞부분으로 찾는다
     */
    @Query("select cu.userId from ChannelUser cu where cu.channelId = :channelId and cu.deleteYn = false")
    List<Long> findMemberUserIds(@Param("channelId") Long channelId);

    /**
     * 마지막으로 읽은 메시지를 앞으로만 옮긴다 (기존 값보다 뒤일 때만). 조건과 갱신을 한 문장으로 해서 동시 요청에도 뒤로 가지 않는다.
     * messageId 는 소문자 hex 24자리여야 한다 (문자열 비교 = ObjectId 순서)
     *
     * @return 갱신된 행 수. 0 이면 멤버가 아니거나 이미 같거나 뒤의 메시지까지 읽음
     */
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("""
        update ChannelUser cu set cu.lastReadMessageId = :messageId
        where cu.channelId = :channelId and cu.userId = :userId and cu.deleteYn = false
          and (cu.lastReadMessageId is null or cu.lastReadMessageId < :messageId)
        """)
    int moveLastReadMessageIdForward(@Param("channelId") Long channelId, @Param("userId") Long userId,
        @Param("messageId") String messageId);
}
