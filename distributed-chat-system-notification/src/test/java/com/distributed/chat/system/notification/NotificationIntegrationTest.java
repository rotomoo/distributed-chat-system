package com.distributed.chat.system.notification;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.mockito.Mockito.atLeast;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.distributed.chat.system.common.event.ChatNotificationEvent;
import com.distributed.chat.system.common.event.EventJson;
import com.distributed.chat.system.common.redis.NotificationKeys;
import com.distributed.chat.system.mysql.entity.Channel;
import com.distributed.chat.system.mysql.entity.ChannelUser;
import com.distributed.chat.system.mysql.entity.User;
import com.distributed.chat.system.mysql.entity.VapidKey;
import com.distributed.chat.system.mysql.entity.WebPushSubscription;
import com.distributed.chat.system.mysql.enums.ChannelType;
import com.distributed.chat.system.mysql.repository.ChannelRepository;
import com.distributed.chat.system.mysql.repository.ChannelUserRepository;
import com.distributed.chat.system.mysql.repository.UserRepository;
import com.distributed.chat.system.mysql.repository.VapidKeyRepository;
import com.distributed.chat.system.mysql.repository.WebPushSubscriptionRepository;
import com.distributed.chat.system.notification.base.kafka.NotificationDeadLetterHandler;
import com.distributed.chat.system.notification.base.webpush.VapidKeys;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpServer;
import jakarta.servlet.http.Cookie;
import java.io.IOException;
import java.math.BigInteger;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.PublicKey;
import java.security.SecureRandom;
import java.security.Security;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;
import javax.crypto.Cipher;
import javax.crypto.KeyAgreement;
import javax.crypto.Mac;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import nl.martijndwars.webpush.Utils;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.common.serialization.StringSerializer;
import org.bouncycastle.jce.ECNamedCurveTable;
import org.bouncycastle.jce.interfaces.ECPublicKey;
import org.bouncycastle.jce.provider.BouncyCastleProvider;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.SpyBean;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.MediaType;
import org.springframework.kafka.core.DefaultKafkaProducerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.test.EmbeddedKafkaBroker;
import org.springframework.kafka.retrytopic.RetryTopicHeaders;
import org.springframework.kafka.test.context.EmbeddedKafka;
import org.springframework.kafka.test.utils.KafkaTestUtils;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextImpl;
import org.springframework.session.Session;
import org.springframework.session.SessionRepository;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

/**
 * 알림 서버 전체 경로 : Kafka(테스트 안 브로커) chat-notification → 음소거 / 구독(로컬 MySQL) → 30초 묶기(로컬 Redis)
 * → 웹 푸시(JDK HttpServer 가 푸시 서비스 대신 암호문과 헤더를 받는다. 브라우저 키로 직접 복호화해서 본문을 확인)
 * + 구독 API (MockMvc, client-api 로그인과 같은 공유 세션)
 * 테스트 데이터는 새로 만든 유저 / 채널로만 하고 끝나면 지운다
 */
@DisplayName("[통합] 알림 서버 (chat-notification 소비 → 웹 푸시, 구독 API) 테스트")
@SpringBootTest(properties = {
    "spring.kafka.bootstrap-servers=${spring.embedded.kafka.brokers}",
    "spring.kafka.consumer.auto-offset-reset=earliest",
    "eureka.client.enabled=false",
    "chat.notification.retry.delay-ms=100",
    "chat.notification.retry.topic-partitions=1",
    "chat.notification.push.allowed-endpoint-hosts=127.0.0.1",
    "chat.notification.push.allow-insecure-endpoint=true",
    "chat.notification.push.ttl-seconds=600",
    "chat.notification.vapid.subject=mailto:test@example.com"
})
@EmbeddedKafka(partitions = 1, topics = {"chat-notification"})
@AutoConfigureMockMvc
class NotificationIntegrationTest {

    private static final Duration WAIT = Duration.ofSeconds(30);
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    private record PushRequest(String path, Map<String, List<String>> headers, byte[] body) {

        String header(String name) {
            return headers.entrySet().stream().filter(entry -> entry.getKey().equalsIgnoreCase(name))
                .map(entry -> entry.getValue().get(0)).findFirst().orElse(null);
        }
    }

    private static final List<PushRequest> RECEIVED = new CopyOnWriteArrayList<>();
    private static final Map<String, Integer> STATUS_BY_PATH = new ConcurrentHashMap<>();
    private static final HttpServer PUSH_SERVICE = startPushService();

    @AfterAll
    static void stopPushService() {
        PUSH_SERVICE.stop(0);
    }

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private EmbeddedKafkaBroker embeddedKafkaBroker;

    @Autowired
    private UserRepository userRepository;

    /**
     * 조회 실패(재시도 / DLT)를 만들 때만 특정 채널에서 예외를 던진다. 그 외에는 진짜 리포지토리
     */
    @SpyBean
    private ChannelRepository channelRepository;

    @SpyBean
    private NotificationDeadLetterHandler notificationDeadLetterHandler;

    @Autowired
    private ChannelUserRepository channelUserRepository;

    @Autowired
    private WebPushSubscriptionRepository webPushSubscriptionRepository;

    @Autowired
    private VapidKeyRepository vapidKeyRepository;

    @Autowired
    private StringRedisTemplate stringRedisTemplate;

    @Autowired
    private VapidKeys vapidKeys;

    @Autowired
    @SuppressWarnings("rawtypes")
    private SessionRepository sessionRepository;

    private KafkaTemplate<String, String> kafkaTemplate;

    private final List<Long> userIds = new ArrayList<>();
    private final List<Long> channelIds = new ArrayList<>();
    private final List<String> sessionIds = new ArrayList<>();

    private Long sender;
    private Long alice;
    private Long bob;
    private Long carol;

    /**
     * 브라우저 쪽 구독 키 (푸시 본문을 복호화할 때 쓴다)
     */
    private record BrowserKeys(KeyPair keyPair, byte[] publicKey, byte[] auth) {

        String p256dh() {
            return Base64.getUrlEncoder().withoutPadding().encodeToString(publicKey);
        }

        String authBase64() {
            return Base64.getUrlEncoder().withoutPadding().encodeToString(auth);
        }
    }

    @BeforeEach
    void setup() {
        RECEIVED.clear();
        STATUS_BY_PATH.clear();
        Map<String, Object> props = KafkaTestUtils.producerProps(embeddedKafkaBroker);
        props.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class);
        props.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, StringSerializer.class);
        kafkaTemplate = new KafkaTemplate<>(new DefaultKafkaProducerFactory<>(props));

        sender = saveUser("철수");
        alice = saveUser("alice");
        bob = saveUser("bob");
        carol = saveUser("carol");
    }

    @AfterEach
    void tearDown() {
        for (Long channelId : channelIds) {
            channelUserRepository.deleteAll(channelUserRepository.findAllByChannelIdAndDeleteYnIsFalse(channelId));
            for (Long userId : userIds) {
                stringRedisTemplate.delete(NotificationKeys.throttleKey(userId, channelId));
            }
        }
        channelRepository.deleteAllById(channelIds);
        webPushSubscriptionRepository.deleteAll(webPushSubscriptionRepository.findAllByUserIdIn(userIds));
        userRepository.deleteAllById(userIds);
        sessionIds.forEach(sessionRepository::deleteById);
        kafkaTemplate.destroy();
    }

    // ── chat-notification → 웹 푸시 ──────────────────────────────

    @Test
    @DisplayName("success case : 음소거 안 한 구독자에게만 aes128gcm 암호문을 VAPID 서명 / TTL / Topic 헤더로 보내고 본문은 #채널명, 보낸사람: 미리보기 다")
    void 음소거_안_한_구독자에게만_암호문을_VAPID_서명_TTL_Topic_헤더로_보낸다() throws Exception {
        // given : alice 구독, bob 구독했지만 음소거, carol 구독 없음
        Long channelId = saveChannel(ChannelType.PUBLIC, "점심메뉴추천-" + UUID.randomUUID().toString().substring(0, 6));
        join(channelId, alice, false);
        join(channelId, bob, true);
        join(channelId, carol, false);
        BrowserKeys aliceKeys = browserKeys();
        String alicePath = subscribe(alice, aliceKeys);
        String bobPath = subscribe(bob, browserKeys());

        // when
        publish(channelId, List.of(alice, bob, carol), "점심 뭐 먹을까요?", 0);

        // then
        await().atMost(WAIT).until(() -> requests(alicePath).size() == 1);
        TimeUnit.MILLISECONDS.sleep(500);
        assertThat(requests(bobPath)).isEmpty();
        assertThat(RECEIVED).hasSize(1);

        PushRequest request = requests(alicePath).get(0);
        assertThat(request.header("TTL")).isEqualTo("600");
        assertThat(request.header("Content-Encoding")).isEqualTo("aes128gcm");
        assertThat(request.header("Topic")).isEqualTo("channel-" + channelId);
        String authorization = request.header("Authorization");
        assertThat(authorization).startsWith("vapid t=").endsWith(", k=" + vapidKeys.publicKey());
        JsonNode jwt = jwtClaims(authorization);
        // aud = endpoint 의 origin (라이브러리는 포트를 넣지 않는다. 실제 푸시 서비스는 443)
        assertThat(jwt.get("aud").asText()).isEqualTo("http://127.0.0.1");
        assertThat(jwt.get("exp").asLong()).isGreaterThan(System.currentTimeMillis() / 1000);
        assertThat(jwt.get("sub").asText()).isEqualTo("mailto:test@example.com");

        JsonNode payload = OBJECT_MAPPER.readTree(decrypt(request.body(), aliceKeys));
        Channel channel = channelRepository.findById(channelId).orElseThrow();
        assertThat(payload.get("title").asText()).isEqualTo("#" + channel.getChannelName());
        assertThat(payload.get("body").asText()).isEqualTo(userName(sender) + ": 점심 뭐 먹을까요?");
        assertThat(payload.get("url").asText()).isEqualTo("/?channel=" + channelId);
        assertThat(payload.get("tag").asText()).isEqualTo("channel-" + channelId);

        // 30초 묶기 키는 실제로 보낸 사람(alice)만 잡는다
        assertThat(stringRedisTemplate.getExpire(NotificationKeys.throttleKey(alice, channelId), TimeUnit.SECONDS)).isBetween(1L, 30L);
        assertThat(stringRedisTemplate.hasKey(NotificationKeys.throttleKey(bob, channelId))).isFalse();
        assertThat(stringRedisTemplate.hasKey(NotificationKeys.throttleKey(carol, channelId))).isFalse();
    }

    @Test
    @DisplayName("success case : DM 은 제목이 보낸 사람 이름이고, 첨부만 보냈으면 본문이 보낸사람: 파일 N개 다")
    void DM_은_제목이_보낸_사람_이름이고_첨부만_보냈으면_파일_N개_다() throws Exception {
        // given
        Long channelId = saveChannel(ChannelType.DM, "dm:" + Math.min(sender, alice) + ":" + Math.max(sender, alice));
        join(channelId, alice, false);
        BrowserKeys aliceKeys = browserKeys();
        String alicePath = subscribe(alice, aliceKeys);

        // when
        publish(channelId, List.of(alice), "", 3);

        // then
        await().atMost(WAIT).until(() -> requests(alicePath).size() == 1);
        JsonNode payload = OBJECT_MAPPER.readTree(decrypt(requests(alicePath).get(0).body(), aliceKeys));
        assertThat(payload.get("title").asText()).isEqualTo(userName(sender));
        assertThat(payload.get("body").asText()).isEqualTo(userName(sender) + ": 파일 3개");
    }

    @Test
    @DisplayName("success case : 같은 (유저, 채널) 은 30초 안에 다시 오면 보내지 않는다 (기기가 여러 개면 기기마다 한 번씩)")
    void 같은_유저_채널은_30초_안에_다시_오면_보내지_않는다() throws Exception {
        // given : alice 는 기기 2대
        Long channelId = saveChannel(ChannelType.PUBLIC, "throttle-" + UUID.randomUUID().toString().substring(0, 6));
        join(channelId, alice, false);
        String pc = subscribe(alice, browserKeys());
        String phone = subscribe(alice, browserKeys());

        // when
        publish(channelId, List.of(alice), "첫 번째", 0);
        await().atMost(WAIT).until(() -> requests(pc).size() == 1 && requests(phone).size() == 1);
        publish(channelId, List.of(alice), "두 번째", 0);
        publish(channelId, List.of(alice), "세 번째", 0);

        // then
        TimeUnit.SECONDS.sleep(1);
        assertThat(requests(pc)).hasSize(1);
        assertThat(requests(phone)).hasSize(1);
    }

    @Test
    @DisplayName("success case : 푸시 서비스가 410 을 주면 그 구독을 지우고, 500 이면 구독을 남긴다")
    void 푸시_서비스가_410_을_주면_구독을_지우고_500_이면_남긴다() {
        // given
        Long channelId = saveChannel(ChannelType.PUBLIC, "expired-" + UUID.randomUUID().toString().substring(0, 6));
        join(channelId, alice, false);
        String gone = subscribe(alice, browserKeys());
        String broken = subscribe(alice, browserKeys());
        STATUS_BY_PATH.put(gone, 410);
        STATUS_BY_PATH.put(broken, 500);

        // when
        publish(channelId, List.of(alice), "hi", 0);

        // then
        await().atMost(WAIT).until(() -> subscriptionOf(endpoint(gone)).isEmpty());
        assertThat(requests(broken)).hasSize(1);
        assertThat(subscriptionOf(endpoint(broken))).isPresent();
    }

    @Test
    @DisplayName("success case : 조회(MySQL)가 한 번 실패하면 재시도 토픽으로 다시 처리해 보낸다")
    void 조회가_한_번_실패하면_재시도_토픽으로_다시_처리해_보낸다() {
        // given
        Long channelId = saveChannel(ChannelType.PUBLIC, "retry-" + UUID.randomUUID().toString().substring(0, 6));
        join(channelId, alice, false);
        String pc = subscribe(alice, browserKeys());
        // 리포지토리 프록시의 진짜 메서드는 부를 수 없어 두 번째는 미리 읽어 둔 채널을 돌려준다
        Optional<Channel> channel = channelRepository.findById(channelId);
        doThrow(new IllegalStateException("mysql down")).doReturn(channel).when(channelRepository).findByIdAndDeleteYnIsFalse(channelId);

        // when
        publish(channelId, List.of(alice), "재시도", 0);

        // then
        await().atMost(WAIT).until(() -> requests(pc).size() == 1);
        verify(channelRepository, times(2)).findByIdAndDeleteYnIsFalse(channelId);
    }

    @Test
    @DisplayName("fail case : 조회가 계속 실패하면 짧게 재시도(3번 시도)한 뒤 DLT 에서 로그만 남기고 버린다")
    void 조회가_계속_실패하면_짧게_재시도한_뒤_DLT_에서_로그만_남기고_버린다() {
        // given
        Long channelId = saveChannel(ChannelType.PUBLIC, "dlt-" + UUID.randomUUID().toString().substring(0, 6));
        join(channelId, alice, false);
        String pc = subscribe(alice, browserKeys());
        doThrow(new IllegalStateException("mysql down")).when(channelRepository).findByIdAndDeleteYnIsFalse(channelId);

        // when
        publish(channelId, List.of(alice), "버림", 0);

        // then
        @SuppressWarnings("unchecked")
        ArgumentCaptor<ConsumerRecord<String, String>> dlt = ArgumentCaptor.forClass(ConsumerRecord.class);
        await().atMost(WAIT).untilAsserted(() -> verify(notificationDeadLetterHandler, atLeast(1)).handle(dlt.capture()));
        ConsumerRecord<String, String> record = dlt.getAllValues().stream()
            .filter(candidate -> String.valueOf(channelId).equals(candidate.key())).findFirst().orElseThrow();
        assertThat(record.topic()).isEqualTo("chat-notification-dlt");
        verify(channelRepository, times(3)).findByIdAndDeleteYnIsFalse(channelId);
        assertThat(requests(pc)).isEmpty();
    }

    @Test
    @DisplayName("fail case : 읽을 수 없는 알림(깨진 JSON, 예전 바이너리 값)은 재시도 없이 바로 DLT 에서 로그만 남기고 버리고, 뒤의 알림은 그대로 보낸다")
    void 읽을_수_없는_알림은_재시도_없이_바로_DLT_에서_버리고_뒤의_알림은_그대로_보낸다() {
        // given
        Long channelId = saveChannel(ChannelType.PUBLIC, "malformed-" + UUID.randomUUID().toString().substring(0, 6));
        join(channelId, alice, false);
        String pc = subscribe(alice, browserKeys());
        String brokenKey = "broken-" + UUID.randomUUID();

        // when : 읽을 수 없는 값 뒤에 정상 알림
        kafkaTemplate.send("chat-notification", brokenKey, "{\"messageId\":").join();
        publish(channelId, List.of(alice), "뒤의 정상 알림", 0);

        // then
        await().atMost(WAIT).until(() -> requests(pc).size() == 1);
        @SuppressWarnings("unchecked")
        ArgumentCaptor<ConsumerRecord<String, String>> dlt = ArgumentCaptor.forClass(ConsumerRecord.class);
        await().atMost(WAIT).untilAsserted(() -> {
            verify(notificationDeadLetterHandler, atLeast(1)).handle(dlt.capture());
            assertThat(dlt.getAllValues()).anyMatch(candidate -> brokenKey.equals(candidate.key()));
        });
        ConsumerRecord<String, String> record = dlt.getAllValues().stream()
            .filter(candidate -> brokenKey.equals(candidate.key())).findFirst().orElseThrow();
        assertThat(record.topic()).isEqualTo("chat-notification-dlt");
        // 원래 토픽에서 1번 실패하고 바로 DLT (재시도 토픽을 거쳤으면 4)
        byte[] attempts = record.headers().lastHeader(RetryTopicHeaders.DEFAULT_HEADER_ATTEMPTS).value();
        assertThat(attempts.length == Integer.BYTES ? ByteBuffer.wrap(attempts).getInt() : new BigInteger(attempts).intValue()).isEqualTo(2);
    }

    // ── 구독 API ────────────────────────────────────────────────

    @Test
    @DisplayName("fail case : 로그인하지 않으면 401")
    void 로그인하지_않으면_401() throws Exception {
        // when & then
        mockMvc.perform(get("/v1/private/api/notification/vapid-public-key")).andExpect(status().isUnauthorized());
        mockMvc.perform(post("/v1/private/api/notification/subscriptions").contentType(MediaType.APPLICATION_JSON)
            .content(subscribeBody(endpoint("/push/x"), browserKeys()))).andExpect(status().isUnauthorized());
        mockMvc.perform(delete("/v1/private/api/notification/subscriptions").contentType(MediaType.APPLICATION_JSON)
            .content("{\"endpoint\":\"" + endpoint("/push/x") + "\"}")).andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("success case : VAPID 공개키를 돌려주고, 환경변수가 없으면 MySQL vapid_key 에 저장된 그 키다")
    void VAPID_공개키를_돌려주고_환경변수가_없으면_vapid_key_에_저장된_그_키다() throws Exception {
        // when & then
        mockMvc.perform(as(alice, get("/v1/private/api/notification/vapid-public-key")))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.publicKey").value(vapidKeys.publicKey()));

        Assumptions.assumeTrue(System.getenv("VAPID_PUBLIC_KEY") == null, "환경변수로 키를 준 경우는 DB 를 보지 않는다");
        VapidKey stored = vapidKeyRepository.findById(VapidKey.SINGLE_ID).orElseThrow();
        assertThat(stored.getPublicKey()).isEqualTo(vapidKeys.publicKey());
        assertThat(stored.getPrivateKey()).isEqualTo(vapidKeys.privateKey());
        // 다시 넣으려 해도 무시된다 (키는 한 번 만들면 고정)
        assertThat(vapidKeyRepository.insertIfAbsent(VapidKey.SINGLE_ID, "other", "other")).isZero();
        assertThat(vapidKeyRepository.findById(VapidKey.SINGLE_ID).orElseThrow().getPublicKey()).isEqualTo(vapidKeys.publicKey());
    }

    @Test
    @DisplayName("success case : 구독하면 로그인 사용자 것으로 저장되고, 같은 endpoint 로 다시 구독하면 한 행이 갱신된다 (주인 / 키)")
    void 구독하면_로그인_사용자_것으로_저장되고_같은_endpoint_로_다시_구독하면_갱신된다() throws Exception {
        // given
        String endpoint = endpoint("/push/" + UUID.randomUUID());
        BrowserKeys first = browserKeys();
        BrowserKeys second = browserKeys();

        // when
        mockMvc.perform(as(alice, post("/v1/private/api/notification/subscriptions")
                .contentType(MediaType.APPLICATION_JSON).content(subscribeBody(endpoint, first))))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.success").value(true));
        WebPushSubscription saved = subscriptionOf(endpoint).orElseThrow();
        mockMvc.perform(as(bob, post("/v1/private/api/notification/subscriptions")
                .contentType(MediaType.APPLICATION_JSON).content(subscribeBody(endpoint, second))))
            .andExpect(status().isOk());

        // then
        assertThat(saved.getUserId()).isEqualTo(alice);
        assertThat(saved.getP256dh()).isEqualTo(first.p256dh());
        List<WebPushSubscription> rows = webPushSubscriptionRepository.findAll().stream()
            .filter(subscription -> subscription.getEndpoint().equals(endpoint)).toList();
        assertThat(rows).hasSize(1);
        assertThat(rows.get(0).getId()).isEqualTo(saved.getId());
        assertThat(rows.get(0).getUserId()).isEqualTo(bob);
        assertThat(rows.get(0).getP256dh()).isEqualTo(second.p256dh());
        assertThat(rows.get(0).getAuth()).isEqualTo(second.authBase64());
    }

    @Test
    @DisplayName("fail case : 허용하지 않은 호스트나 키가 틀린 구독은 5001, 키가 빠지면 400 으로 거절하고 저장하지 않는다")
    void 허용하지_않은_호스트나_키가_틀린_구독은_거절하고_저장하지_않는다() throws Exception {
        // given
        String evil = "http://10.0.0.1:" + PUSH_SERVICE.getAddress().getPort() + "/push/" + UUID.randomUUID();
        String badKeys = endpoint("/push/" + UUID.randomUUID());

        // when & then
        mockMvc.perform(as(alice, post("/v1/private/api/notification/subscriptions")
                .contentType(MediaType.APPLICATION_JSON).content(subscribeBody(evil, browserKeys()))))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value(5001));
        mockMvc.perform(as(alice, post("/v1/private/api/notification/subscriptions")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"endpoint\":\"" + badKeys + "\",\"keys\":{\"p256dh\":\"AAAA\",\"auth\":\"AAAA\"}}")))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value(5001));
        mockMvc.perform(as(alice, post("/v1/private/api/notification/subscriptions")
                .contentType(MediaType.APPLICATION_JSON).content("{\"endpoint\":\"" + badKeys + "\"}")))
            .andExpect(status().isBadRequest());
        assertThat(subscriptionOf(evil)).isEmpty();
        assertThat(subscriptionOf(badKeys)).isEmpty();
    }

    @Test
    @DisplayName("success case : 구독 해제는 내 구독만 지운다 (남의 endpoint 로는 못 지움)")
    void 구독_해제는_내_구독만_지운다() throws Exception {
        // given
        String path = subscribe(alice, browserKeys());
        String body = "{\"endpoint\":\"" + endpoint(path) + "\"}";

        // when
        mockMvc.perform(as(bob, delete("/v1/private/api/notification/subscriptions").contentType(MediaType.APPLICATION_JSON).content(body)))
            .andExpect(status().isOk());
        boolean afterOther = subscriptionOf(endpoint(path)).isPresent();
        mockMvc.perform(as(alice, delete("/v1/private/api/notification/subscriptions").contentType(MediaType.APPLICATION_JSON).content(body)))
            .andExpect(status().isOk());

        // then
        assertThat(afterOther).isTrue();
        assertThat(subscriptionOf(endpoint(path))).isEmpty();
    }

    // ── 도우미 ──────────────────────────────────────────────────

    private void publish(Long channelId, List<Long> receivers, String preview, int attachments) {
        ChatNotificationEvent notification = new ChatNotificationEvent(UUID.randomUUID().toString(), channelId, sender, preview, attachments,
            System.currentTimeMillis(), receivers);
        kafkaTemplate.send("chat-notification", String.valueOf(channelId), EventJson.write(notification)).join();
    }

    /**
     * API 로 구독하고 그 endpoint 의 경로를 돌려준다
     */
    private String subscribe(Long userId, BrowserKeys keys) {
        String path = "/push/" + UUID.randomUUID();
        try {
            mockMvc.perform(as(userId, post("/v1/private/api/notification/subscriptions")
                    .contentType(MediaType.APPLICATION_JSON).content(subscribeBody(endpoint(path), keys))))
                .andExpect(status().isOk());
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
        return path;
    }

    private static String subscribeBody(String endpoint, BrowserKeys keys) {
        return "{\"endpoint\":\"" + endpoint + "\",\"keys\":{\"p256dh\":\"" + keys.p256dh() + "\",\"auth\":\"" + keys.authBase64() + "\"}}";
    }

    private static String endpoint(String path) {
        return "http://127.0.0.1:" + PUSH_SERVICE.getAddress().getPort() + path;
    }

    private Optional<WebPushSubscription> subscriptionOf(String endpoint) {
        return webPushSubscriptionRepository.findAll().stream().filter(subscription -> subscription.getEndpoint().equals(endpoint)).findFirst();
    }

    private static List<PushRequest> requests(String path) {
        return RECEIVED.stream().filter(request -> request.path().equals(path)).toList();
    }

    /**
     * client-api 로그인이 만드는 것과 같은 공유 세션 (SPRING_SECURITY_CONTEXT, principal = userdetails.User(name = userId))
     */
    @SuppressWarnings("unchecked")
    private MockHttpServletRequestBuilder as(Long userId, MockHttpServletRequestBuilder request) {
        Session session = sessionRepository.createSession();
        org.springframework.security.core.userdetails.User principal =
            new org.springframework.security.core.userdetails.User(String.valueOf(userId), "N/A", List.of());
        session.setAttribute("SPRING_SECURITY_CONTEXT", new SecurityContextImpl(
            UsernamePasswordAuthenticationToken.authenticated(principal, null, List.of())));
        session.setAttribute("userId", String.valueOf(userId));
        sessionRepository.save(session);
        sessionIds.add(session.getId());
        return request.cookie(new Cookie("SESSION", Base64.getEncoder().encodeToString(session.getId().getBytes(StandardCharsets.UTF_8))));
    }

    private Long saveUser(String name) {
        String account = name + "_" + UUID.randomUUID().toString().substring(0, 8);
        Long id = userRepository.save(User.builder().userName(account).account(account).password("hash").build()).getId();
        userIds.add(id);
        return id;
    }

    private String userName(Long userId) {
        return userRepository.findById(userId).orElseThrow().getUserName();
    }

    private Long saveChannel(ChannelType type, String name) {
        Long id = channelRepository.save(Channel.builder().teamId(ThreadLocalRandom.current().nextLong(3_000_000_000L, 4_000_000_000L))
            .channelName(name).channelType(type).build()).getId();
        channelIds.add(id);
        return id;
    }

    private void join(Long channelId, Long userId, boolean muted) {
        channelUserRepository.save(ChannelUser.builder().channelId(channelId).userId(userId).notificationMuted(muted).deleteYn(false).build());
    }

    private static BrowserKeys browserKeys() {
        try {
            if (Security.getProvider(BouncyCastleProvider.PROVIDER_NAME) == null) {
                Security.addProvider(new BouncyCastleProvider());
            }
            KeyPairGenerator generator = KeyPairGenerator.getInstance("ECDH", BouncyCastleProvider.PROVIDER_NAME);
            generator.initialize(ECNamedCurveTable.getParameterSpec("prime256v1"));
            KeyPair keyPair = generator.generateKeyPair();
            byte[] auth = new byte[16];
            new SecureRandom().nextBytes(auth);
            return new BrowserKeys(keyPair, Utils.encode((ECPublicKey) keyPair.getPublic()), auth);
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    private static JsonNode jwtClaims(String authorization) throws IOException {
        String jwt = authorization.substring("vapid t=".length(), authorization.indexOf(", k="));
        return OBJECT_MAPPER.readTree(Base64.getUrlDecoder().decode(jwt.split("\\.")[1]));
    }

    /**
     * 브라우저가 하는 복호화 (RFC 8291 / RFC 8188 aes128gcm, 레코드 하나)
     * 본문 = salt(16) | rs(4) | idlen(1) | 서버 임시 공개키(65) | 암호문
     */
    private static byte[] decrypt(byte[] body, BrowserKeys keys) throws Exception {
        ByteBuffer buffer = ByteBuffer.wrap(body);
        byte[] salt = new byte[16];
        buffer.get(salt);
        buffer.getInt();
        byte[] serverPublic = new byte[buffer.get() & 0xFF];
        buffer.get(serverPublic);
        byte[] ciphertext = new byte[buffer.remaining()];
        buffer.get(ciphertext);

        PublicKey serverKey = Utils.loadPublicKey(serverPublic);
        KeyAgreement agreement = KeyAgreement.getInstance("ECDH", BouncyCastleProvider.PROVIDER_NAME);
        agreement.init(keys.keyPair().getPrivate());
        agreement.doPhase(serverKey, true);
        byte[] ecdhSecret = agreement.generateSecret();

        byte[] keyInfo = concat("WebPush: info".getBytes(StandardCharsets.US_ASCII), new byte[]{0}, keys.publicKey(), serverPublic);
        byte[] ikm = hkdf(keys.auth(), ecdhSecret, keyInfo, 32);
        byte[] contentKey = hkdf(salt, ikm, concat("Content-Encoding: aes128gcm".getBytes(StandardCharsets.US_ASCII), new byte[]{0}), 16);
        byte[] nonce = hkdf(salt, ikm, concat("Content-Encoding: nonce".getBytes(StandardCharsets.US_ASCII), new byte[]{0}), 12);

        Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
        cipher.init(Cipher.DECRYPT_MODE, new SecretKeySpec(contentKey, "AES"), new GCMParameterSpec(128, nonce));
        byte[] padded = cipher.doFinal(ciphertext);
        // 마지막 레코드 : 본문 | 0x02 | 0x00...
        int end = padded.length - 1;
        while (padded[end] == 0) {
            end--;
        }
        assertThat(padded[end]).isEqualTo((byte) 0x02);
        return Arrays.copyOf(padded, end);
    }

    private static byte[] hkdf(byte[] salt, byte[] ikm, byte[] info, int length) throws Exception {
        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec(salt, "HmacSHA256"));
        byte[] prk = mac.doFinal(ikm);
        mac.init(new SecretKeySpec(prk, "HmacSHA256"));
        return Arrays.copyOf(mac.doFinal(concat(info, new byte[]{1})), length);
    }

    private static byte[] concat(byte[]... parts) {
        ByteBuffer buffer = ByteBuffer.allocate(Arrays.stream(parts).mapToInt(part -> part.length).sum());
        Arrays.stream(parts).forEach(buffer::put);
        return buffer.array();
    }

    private static HttpServer startPushService() {
        try {
            HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
            server.setExecutor(Executors.newCachedThreadPool());
            server.createContext("/", exchange -> {
                String path = exchange.getRequestURI().getPath();
                RECEIVED.add(new PushRequest(path, Map.copyOf(exchange.getRequestHeaders()), exchange.getRequestBody().readAllBytes()));
                exchange.sendResponseHeaders(STATUS_BY_PATH.getOrDefault(path, 201), -1);
                exchange.close();
            });
            server.start();
            return server;
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
    }
}
