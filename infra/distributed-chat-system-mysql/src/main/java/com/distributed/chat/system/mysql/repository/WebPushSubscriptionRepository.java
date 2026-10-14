package com.distributed.chat.system.mysql.repository;

import com.distributed.chat.system.mysql.entity.WebPushSubscription;
import java.util.Collection;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

public interface WebPushSubscriptionRepository extends JpaRepository<WebPushSubscription, Long> {

    /**
     * 받는 사람들의 구독 전부 (알림 이벤트 한 건에 한 번 조회)
     */
    List<WebPushSubscription> findAllByUserIdIn(Collection<Long> userIds);

    /**
     * endpoint 로 넣거나 갱신한다 (주인 / 키도 바꾼다). 문장 하나라 같은 endpoint 가 동시에 와도 유니크 키 오류 없이 한 행이 된다
     */
    @Transactional
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query(value = """
        insert into web_push_subscription (user_id, endpoint, p256dh, auth)
        values (:userId, :endpoint, :p256dh, :auth) as incoming
        on duplicate key update user_id = incoming.user_id, p256dh = incoming.p256dh, auth = incoming.auth
        """, nativeQuery = true)
    int upsert(@Param("userId") Long userId, @Param("endpoint") String endpoint, @Param("p256dh") String p256dh,
        @Param("auth") String auth);

    /**
     * 내 구독만 지운다 (남의 endpoint 를 알아도 못 지움)
     */
    @Transactional
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("delete from WebPushSubscription s where s.endpoint = :endpoint and s.userId = :userId")
    int deleteByEndpointAndUserId(@Param("endpoint") String endpoint, @Param("userId") Long userId);

    /**
     * 푸시 서비스가 404 / 410 (구독 만료) 을 주면 지운다
     */
    @Transactional
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("delete from WebPushSubscription s where s.endpoint = :endpoint")
    int deleteByEndpoint(@Param("endpoint") String endpoint);
}
