package com.distributed.chat.system.client.api.web.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.distributed.chat.system.mysql.entity.User;
import com.distributed.chat.system.mysql.entity.WebPushSubscription;
import com.distributed.chat.system.mysql.repository.UserRepository;
import com.distributed.chat.system.mysql.repository.WebPushSubscriptionRepository;
import jakarta.transaction.Transactional;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

/**
 * 진짜 MySQL (로컬 docker 스택), 트랜잭션 롤백. endpoint 는 허용 호스트(fcm.googleapis.com) 모양만 쓰고 실제로 보내지 않는다 (보내기는 notification-consumer).
 * VAPID 공개키는 환경변수 경로로 확인한다 (MySQL vapid_key 경로는 [단위] PushSubscriptionService / notification 통합 테스트)
 */
@DisplayName("[통합] 웹 푸시 구독 API 테스트")
// 테스트 JVM 이 실행 중인 스택의 Eureka 에 등록되면 게이트웨이가 실제 요청을 테스트로 보낼 수 있으므로 끈다
@SpringBootTest(properties = {
    "eureka.client.enabled=false",
    "chat.notification.vapid.public-key=BTestConfiguredPublicKey"
})
@AutoConfigureMockMvc
@Transactional
class PushSubscriptionApiIntegrationTest {

    private static final String BASE = "/v1/private/api/notification";
    private static final SecureRandom RANDOM = new SecureRandom();

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private WebPushSubscriptionRepository webPushSubscriptionRepository;

    private Long alice;
    private Long bob;

    @BeforeEach
    void setup() {
        alice = saveUser("alice");
        bob = saveUser("bob");
    }

    @Test
    @DisplayName("fail case : 로그인하지 않으면 401 / 1003 이다")
    void 로그인하지_않으면_401_1003_이다() throws Exception {
        // when & then
        mockMvc.perform(get(BASE + "/vapid-public-key"))
            .andExpect(status().isUnauthorized())
            .andExpect(jsonPath("$.code").value(1003));
        mockMvc.perform(post(BASE + "/subscriptions").contentType(MediaType.APPLICATION_JSON)
                .content(subscribeBody(endpoint(), p256dh(), auth())))
            .andExpect(status().isUnauthorized())
            .andExpect(jsonPath("$.code").value(1003));
        mockMvc.perform(delete(BASE + "/subscriptions").contentType(MediaType.APPLICATION_JSON)
                .content("{\"endpoint\":\"" + endpoint() + "\"}"))
            .andExpect(status().isUnauthorized())
            .andExpect(jsonPath("$.code").value(1003));
    }

    @Test
    @DisplayName("success case : VAPID 공개키를 돌려준다 (환경변수 VAPID_PUBLIC_KEY = notification-consumer 와 같은 값)")
    void VAPID_공개키를_돌려준다() throws Exception {
        // when & then
        mockMvc.perform(as(alice, get(BASE + "/vapid-public-key")))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.success").value(true))
            .andExpect(jsonPath("$.data.publicKey").value("BTestConfiguredPublicKey"));
    }

    @Test
    @DisplayName("success case : 구독하면 로그인 사용자 것으로 저장되고, 같은 endpoint 로 다시 구독하면 한 행이 갱신된다 (주인 / 키)")
    void 구독하면_로그인_사용자_것으로_저장되고_같은_endpoint_로_다시_구독하면_갱신된다() throws Exception {
        // given
        String endpoint = endpoint();
        String firstKey = p256dh();
        String secondKey = p256dh();
        String secondAuth = auth();

        // when
        mockMvc.perform(as(alice, post(BASE + "/subscriptions")
                .contentType(MediaType.APPLICATION_JSON).content(subscribeBody(endpoint, firstKey, auth()))))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.success").value(true));
        List<WebPushSubscription> afterAlice = subscriptionsOf(endpoint);
        mockMvc.perform(as(bob, post(BASE + "/subscriptions")
                .contentType(MediaType.APPLICATION_JSON).content(subscribeBody(endpoint, secondKey, secondAuth))))
            .andExpect(status().isOk());

        // then
        assertThat(afterAlice).hasSize(1);
        assertThat(afterAlice.get(0).getUserId()).isEqualTo(alice);
        assertThat(afterAlice.get(0).getP256dh()).isEqualTo(firstKey);
        List<WebPushSubscription> rows = subscriptionsOf(endpoint);
        assertThat(rows).hasSize(1);
        assertThat(rows.get(0).getId()).isEqualTo(afterAlice.get(0).getId());
        assertThat(rows.get(0).getUserId()).isEqualTo(bob);
        assertThat(rows.get(0).getP256dh()).isEqualTo(secondKey);
        assertThat(rows.get(0).getAuth()).isEqualTo(secondAuth);
    }

    @Test
    @DisplayName("fail case : 허용하지 않은 호스트나 키가 틀린 구독은 5001, 키가 빠지면 400 으로 거절하고 저장하지 않는다")
    void 허용하지_않은_호스트나_키가_틀린_구독은_거절하고_저장하지_않는다() throws Exception {
        // given
        String evil = "https://10.0.0.1/push/" + UUID.randomUUID();
        String badKeys = endpoint();

        // when & then
        mockMvc.perform(as(alice, post(BASE + "/subscriptions")
                .contentType(MediaType.APPLICATION_JSON).content(subscribeBody(evil, p256dh(), auth()))))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value(5001));
        mockMvc.perform(as(alice, post(BASE + "/subscriptions")
                .contentType(MediaType.APPLICATION_JSON).content(subscribeBody(badKeys, "AAAA", "AAAA"))))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value(5001));
        mockMvc.perform(as(alice, post(BASE + "/subscriptions")
                .contentType(MediaType.APPLICATION_JSON).content("{\"endpoint\":\"" + badKeys + "\"}")))
            .andExpect(status().isBadRequest());
        assertThat(subscriptionsOf(evil)).isEmpty();
        assertThat(subscriptionsOf(badKeys)).isEmpty();
    }

    @Test
    @DisplayName("success case : 구독 해제는 내 구독만 지운다 (남의 endpoint 로는 못 지움)")
    void 구독_해제는_내_구독만_지운다() throws Exception {
        // given
        String endpoint = endpoint();
        mockMvc.perform(as(alice, post(BASE + "/subscriptions")
                .contentType(MediaType.APPLICATION_JSON).content(subscribeBody(endpoint, p256dh(), auth()))))
            .andExpect(status().isOk());
        String body = "{\"endpoint\":\"" + endpoint + "\"}";

        // when
        mockMvc.perform(as(bob, delete(BASE + "/subscriptions").contentType(MediaType.APPLICATION_JSON).content(body)))
            .andExpect(status().isOk());
        boolean afterOther = !subscriptionsOf(endpoint).isEmpty();
        mockMvc.perform(as(alice, delete(BASE + "/subscriptions").contentType(MediaType.APPLICATION_JSON).content(body)))
            .andExpect(status().isOk());

        // then
        assertThat(afterOther).isTrue();
        assertThat(subscriptionsOf(endpoint)).isEmpty();
    }

    private List<WebPushSubscription> subscriptionsOf(String endpoint) {
        return webPushSubscriptionRepository.findAll().stream().filter(subscription -> subscription.getEndpoint().equals(endpoint)).toList();
    }

    private static String subscribeBody(String endpoint, String p256dh, String auth) {
        return "{\"endpoint\":\"" + endpoint + "\",\"keys\":{\"p256dh\":\"" + p256dh + "\",\"auth\":\"" + auth + "\"}}";
    }

    private static String endpoint() {
        return "https://fcm.googleapis.com/fcm/send/" + UUID.randomUUID();
    }

    /**
     * 브라우저 키 모양 : 비압축 P-256 공개키 65바이트 (0x04 로 시작), base64url
     */
    private static String p256dh() {
        byte[] key = new byte[65];
        RANDOM.nextBytes(key);
        key[0] = 0x04;
        return Base64.getUrlEncoder().withoutPadding().encodeToString(key);
    }

    private static String auth() {
        byte[] auth = new byte[16];
        RANDOM.nextBytes(auth);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(auth);
    }

    private MockHttpServletRequestBuilder as(Long userId, MockHttpServletRequestBuilder request) {
        return request.with(user(String.valueOf(userId)));
    }

    private Long saveUser(String name) {
        String account = name + "_" + UUID.randomUUID().toString().substring(0, 8);
        return userRepository.save(User.builder().userName(account).account(account).password("hash").build()).getId();
    }
}
