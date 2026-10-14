package com.distributed.chat.system.mysql.repository;

import static org.assertj.core.api.Assertions.assertThat;

import com.distributed.chat.system.mysql.entity.VapidKey;
import com.distributed.chat.system.mysql.entity.WebPushSubscription;
import jakarta.transaction.Transactional;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

@DisplayName("[통합] WebPushSubscriptionRepository / VapidKeyRepository 테스트")
@SpringBootTest
@Transactional
class WebPushSubscriptionRepositoryIntegrationTest {

    @Autowired
    private WebPushSubscriptionRepository webPushSubscriptionRepository;

    @Autowired
    private VapidKeyRepository vapidKeyRepository;

    private Long userId;
    private String endpoint;

    @BeforeEach
    void setup() {
        // 실제 데이터와 겹치지 않는 큰 ID / 새 endpoint (트랜잭션 롤백으로 지워진다)
        userId = ThreadLocalRandom.current().nextLong(3_000_000_000L, 4_000_000_000L);
        endpoint = "https://fcm.googleapis.com/fcm/send/" + UUID.randomUUID();
    }

    @Test
    @DisplayName("success case : 처음 구독은 넣고, 같은 endpoint 는 한 행을 갱신한다 (주인 / 키)")
    void 처음_구독은_넣고_같은_endpoint_는_한_행을_갱신한다() {
        // given
        webPushSubscriptionRepository.upsert(userId, endpoint, "p1", "a1");
        WebPushSubscription first = only(endpoint);

        // when
        webPushSubscriptionRepository.upsert(userId + 1, endpoint, "p2", "a2");

        // then
        WebPushSubscription updated = only(endpoint);
        assertThat(updated.getId()).isEqualTo(first.getId());
        assertThat(updated.getUserId()).isEqualTo(userId + 1);
        assertThat(updated.getP256dh()).isEqualTo("p2");
        assertThat(updated.getAuth()).isEqualTo("a2");
        assertThat(updated.getCreateDt()).isNotNull();
    }

    @Test
    @DisplayName("success case : endpoint 는 대소문자를 구분한다 (토큰이 대소문자만 다른 두 기기는 따로 저장)")
    void endpoint_는_대소문자를_구분한다() {
        // given
        String upper = endpoint + "-AbC";
        String lower = endpoint + "-abc";

        // when
        webPushSubscriptionRepository.upsert(userId, upper, "p1", "a1");
        webPushSubscriptionRepository.upsert(userId + 1, lower, "p2", "a2");

        // then
        assertThat(only(upper).getUserId()).isEqualTo(userId);
        assertThat(only(lower).getUserId()).isEqualTo(userId + 1);
    }

    @Test
    @DisplayName("success case : 받는 사람들의 구독을 한 번에 읽는다 (기기마다 한 행)")
    void 받는_사람들의_구독을_한_번에_읽는다() {
        // given
        webPushSubscriptionRepository.upsert(userId, endpoint, "p", "a");
        webPushSubscriptionRepository.upsert(userId, endpoint + "-phone", "p", "a");
        webPushSubscriptionRepository.upsert(userId + 1, endpoint + "-other", "p", "a");
        webPushSubscriptionRepository.upsert(userId + 2, endpoint + "-not-asked", "p", "a");

        // when
        List<WebPushSubscription> found = webPushSubscriptionRepository.findAllByUserIdIn(List.of(userId, userId + 1));

        // then
        assertThat(found).extracting(WebPushSubscription::getEndpoint)
            .containsExactlyInAnyOrder(endpoint, endpoint + "-phone", endpoint + "-other");
    }

    @Test
    @DisplayName("success case : 구독 해제는 주인만 지울 수 있고, 만료 정리는 endpoint 로 지운다")
    void 구독_해제는_주인만_지울_수_있고_만료_정리는_endpoint_로_지운다() {
        // given
        webPushSubscriptionRepository.upsert(userId, endpoint, "p", "a");
        webPushSubscriptionRepository.upsert(userId, endpoint + "-expired", "p", "a");

        // when
        int byOther = webPushSubscriptionRepository.deleteByEndpointAndUserId(endpoint, userId + 1);
        int byOwner = webPushSubscriptionRepository.deleteByEndpointAndUserId(endpoint, userId);
        int expired = webPushSubscriptionRepository.deleteByEndpoint(endpoint + "-expired");

        // then
        assertThat(byOther).isZero();
        assertThat(byOwner).isEqualTo(1);
        assertThat(expired).isEqualTo(1);
        assertThat(webPushSubscriptionRepository.findAllByUserIdIn(List.of(userId))).isEmpty();
    }

    @Test
    @DisplayName("success case : VAPID 키는 id = 1 한 행만 생기고 두 번째 넣기는 무시된다 (기존 값 유지)")
    void VAPID_키는_한_행만_생기고_두_번째_넣기는_무시된다() {
        // given : 이미 있으면 그 값 (실제 데이터를 바꾸지 않는다), 없으면 이 트랜잭션에서 넣고 롤백
        String before = vapidKeyRepository.findById(VapidKey.SINGLE_ID).map(VapidKey::getPublicKey).orElse(null);
        int first = vapidKeyRepository.insertIfAbsent(VapidKey.SINGLE_ID, "first-public", "first-private");

        // when
        int second = vapidKeyRepository.insertIfAbsent(VapidKey.SINGLE_ID, "second-public", "second-private");

        // then
        assertThat(first).isEqualTo(before == null ? 1 : 0);
        assertThat(second).isZero();
        assertThat(vapidKeyRepository.findAll()).hasSize(1);
        assertThat(vapidKeyRepository.findById(VapidKey.SINGLE_ID).orElseThrow().getPublicKey())
            .isEqualTo(before == null ? "first-public" : before);
    }

    private WebPushSubscription only(String endpoint) {
        List<WebPushSubscription> rows = webPushSubscriptionRepository.findAll().stream()
            .filter(subscription -> subscription.getEndpoint().equals(endpoint)).toList();
        assertThat(rows).hasSize(1);
        return rows.get(0);
    }
}
