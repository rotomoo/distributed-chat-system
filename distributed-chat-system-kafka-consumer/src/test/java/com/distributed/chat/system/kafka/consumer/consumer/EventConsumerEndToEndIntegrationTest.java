package com.distributed.chat.system.kafka.consumer.consumer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import com.distributed.chat.system.mongodb.collection.ChatMessage;
import com.distributed.chat.system.mysql.entity.ChannelUser;
import com.distributed.chat.system.mysql.repository.ChannelUserRepository;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.common.serialization.ByteArraySerializer;
import org.apache.kafka.common.serialization.StringSerializer;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.kafka.core.DefaultKafkaProducerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.test.EmbeddedKafkaBroker;
import org.springframework.kafka.test.context.EmbeddedKafka;
import org.springframework.kafka.test.utils.KafkaTestUtils;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import protobuf.EdaChatMessage;
import protobuf.EdaChatMessage.ChatMessageDelivery;
import protobuf.EdaChatMessage.ChatReaction;
import protobuf.EdaChatMessage.ChatReactionDelivery;

/**
 * 컨슈머 전체 경로 : Kafka(테스트 안 브로커) → 멤버 확인(로컬 MySQL channel_user) → 저장(로컬 MongoDB)
 * → 접속 위치 MGET(로컬 Redis) → 살아 있는 채팅 서버(simple discovery, JDK HttpServer 가 채팅 서버 대신)로 전달.
 * 테스트 데이터는 실제 데이터와 겹치지 않는 큰 ID 를 쓰고 끝나면 지운다
 */
@DisplayName("[통합] 컨슈머 전체 경로 (MySQL 멤버 → MongoDB 저장 → Redis 위치 → 채팅 서버 전달) 테스트")
@SpringBootTest(properties = {
    "spring.kafka.bootstrap-servers=${spring.embedded.kafka.brokers}",
    "spring.kafka.consumer.auto-offset-reset=earliest",
    "chat.kafka.retry.delays-ms=100,200,300",
    "chat.kafka.retry.topic-partitions=1",
    "eureka.client.enabled=false"
})
@EmbeddedKafka(partitions = 1, topics = {"chat-send-message", "chat-reaction"})
class EventConsumerEndToEndIntegrationTest {

    private static final Duration WAIT = Duration.ofSeconds(30);

    private record ReceivedRequest(String path, byte[] body) {
    }

    private static final List<ReceivedRequest> RECEIVED = new CopyOnWriteArrayList<>();
    private static final AtomicInteger FAILURES_LEFT = new AtomicInteger();
    private static final HttpServer CHAT_SERVER = startChatServer();

    @DynamicPropertySource
    static void chatServerProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.cloud.discovery.client.simple.instances.chatting[0].uri", () -> "http://127.0.0.1:" + CHAT_SERVER.getAddress().getPort());
    }

    @AfterAll
    static void stopChatServer() {
        CHAT_SERVER.stop(0);
    }

    @Autowired
    private EmbeddedKafkaBroker embeddedKafkaBroker;

    @Autowired
    private ChannelUserRepository channelUserRepository;

    @Autowired
    private RedisTemplate<String, Object> redisTemplate;

    @Autowired
    @Qualifier("notificationMongoTemplate")
    private MongoTemplate mongoTemplate;

    private KafkaTemplate<String, byte[]> kafkaTemplate;

    private Long channelId;
    private Long sender;
    private Long online;
    private Long offline;
    private Long onDeadServer;
    private Long outsider;
    private final List<String> eventIds = new ArrayList<>();

    @BeforeEach
    void setup() {
        RECEIVED.clear();
        FAILURES_LEFT.set(0);
        Map<String, Object> props = KafkaTestUtils.producerProps(embeddedKafkaBroker);
        props.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class);
        props.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, ByteArraySerializer.class);
        kafkaTemplate = new KafkaTemplate<>(new DefaultKafkaProducerFactory<>(props));

        // 채널 멤버 : 보낸 사람, 접속 중, 앱 꺼짐, 죽은 서버를 가리킴. outsider 는 멤버 아님
        channelId = ThreadLocalRandom.current().nextLong(3_000_000_000L, 4_000_000_000L);
        long base = ThreadLocalRandom.current().nextLong(3_000_000_000L, 4_000_000_000L);
        sender = base;
        online = base + 1;
        offline = base + 2;
        onDeadServer = base + 3;
        outsider = base + 4;
        for (Long member : List.of(sender, online, offline, onDeadServer)) {
            channelUserRepository.save(ChannelUser.builder().channelId(channelId).userId(member).deleteYn(false).build());
        }

        String chatServer = "ws://127.0.0.1:" + CHAT_SERVER.getAddress().getPort();
        redisTemplate.opsForValue().set(String.valueOf(sender), chatServer);
        redisTemplate.opsForValue().set(String.valueOf(online), chatServer);
        redisTemplate.opsForValue().set(String.valueOf(onDeadServer), "ws://10.255.255.7:1");
        redisTemplate.opsForValue().set(String.valueOf(outsider), chatServer);
    }

    @AfterEach
    void tearDown() {
        channelUserRepository.deleteAll(channelUserRepository.findAllByChannelIdAndDeleteYnIsFalse(channelId));
        redisTemplate.delete(List.of(String.valueOf(sender), String.valueOf(online), String.valueOf(onDeadServer), String.valueOf(outsider)));
        mongoTemplate.remove(Query.query(Criteria.where("channelId").is(channelId)), ChatMessage.class);
        kafkaTemplate.destroy();
    }

    @Test
    @DisplayName("success case : 멤버가 보낸 메시지는 저장하고, 살아 있는 서버에 접속한 멤버(보낸 사람 포함)에게만 서버당 한 번 보낸다")
    void 멤버가_보낸_메시지는_저장하고_살아_있는_서버에_접속한_멤버에게만_서버당_한_번_보낸다() throws Exception {
        // given
        String eventId = newEventId();

        // when
        publishMessage(eventId, sender, "점심 뭐 먹을까요?");

        // then
        await().atMost(WAIT).until(() -> deliveries("/internal/chat/deliver").size() == 1);
        ChatMessageDelivery delivery = ChatMessageDelivery.parseFrom(deliveries("/internal/chat/deliver").get(0));
        assertThat(delivery.getReceiveUserIdsList()).containsExactlyInAnyOrder(sender, online);
        assertThat(delivery.getChatMessage().getMessage()).isEqualTo("점심 뭐 먹을까요?");
        ChatMessage saved = mongoTemplate.findOne(Query.query(Criteria.where("eventId").is(eventId)), ChatMessage.class);
        assertThat(saved).isNotNull();
        assertThat(delivery.getMessageId()).isEqualTo(saved.getId().toHexString());
    }

    @Test
    @DisplayName("fail case : 채널 멤버가 아닌 사람이 보낸 메시지는 저장하지도 보내지도 않는다")
    void 채널_멤버가_아닌_사람이_보낸_메시지는_저장하지도_보내지도_않는다() throws Exception {
        // given
        String dropped = newEventId();
        String marker = newEventId();

        // when : 멤버 아닌 메시지 다음에 멤버 메시지를 보내서, 뒤의 것이 처리될 때 앞의 것도 처리가 끝났음을 안다
        publishMessage(dropped, outsider, "몰래");
        publishMessage(marker, sender, "정상");

        // then
        await().atMost(WAIT).until(() -> deliveries("/internal/chat/deliver").size() == 1);
        assertThat(ChatMessageDelivery.parseFrom(deliveries("/internal/chat/deliver").get(0)).getChatMessage().getMessage()).isEqualTo("정상");
        assertThat(mongoTemplate.count(Query.query(Criteria.where("eventId").is(dropped)), ChatMessage.class)).isZero();
    }

    @Test
    @DisplayName("success case : 리액션은 반영한 뒤 바뀐 reactions 전체를 접속한 멤버에게 보낸다")
    void 리액션은_반영한_뒤_바뀐_reactions_전체를_접속한_멤버에게_보낸다() throws Exception {
        // given
        String eventId = newEventId();
        publishMessage(eventId, sender, "리액션 대상");
        await().atMost(WAIT).until(() -> deliveries("/internal/chat/deliver").size() == 1);
        String messageId = ChatMessageDelivery.parseFrom(deliveries("/internal/chat/deliver").get(0)).getMessageId();

        // when
        kafkaTemplate.send("chat-reaction", String.valueOf(channelId), ChatReaction.newBuilder().setSendUserId(online)
            .setChannelId(channelId).setMessageId(messageId).setEmoji("👍").setAction(ChatReaction.Action.ADD).build().toByteArray()).join();

        // then
        await().atMost(WAIT).until(() -> deliveries("/internal/chat/deliver-reaction").size() == 1);
        ChatReactionDelivery delivery = ChatReactionDelivery.parseFrom(deliveries("/internal/chat/deliver-reaction").get(0));
        assertThat(delivery.getMessageId()).isEqualTo(messageId);
        assertThat(delivery.getReceiveUserIdsList()).containsExactlyInAnyOrder(sender, online);
        assertThat(delivery.getReactions(0).getEmoji()).isEqualTo("👍");
        assertThat(delivery.getReactions(0).getUserIdsList()).containsExactly(online);
    }

    @Test
    @DisplayName("success case : 채팅 서버가 한 번 실패하면 재시도 토픽으로 다시 보내고 문서는 하나다 (같은 messageId)")
    void 채팅_서버가_한_번_실패하면_재시도_토픽으로_다시_보내고_문서는_하나다() throws Exception {
        // given
        String eventId = newEventId();
        FAILURES_LEFT.set(1);

        // when
        publishMessage(eventId, sender, "한 번 실패");

        // then
        await().atMost(WAIT).until(() -> deliveries("/internal/chat/deliver").size() == 2);
        String first = ChatMessageDelivery.parseFrom(deliveries("/internal/chat/deliver").get(0)).getMessageId();
        String second = ChatMessageDelivery.parseFrom(deliveries("/internal/chat/deliver").get(1)).getMessageId();
        assertThat(second).isEqualTo(first);
        assertThat(mongoTemplate.count(Query.query(Criteria.where("eventId").is(eventId)), ChatMessage.class)).isEqualTo(1);
        TimeUnit.MILLISECONDS.sleep(500);
        assertThat(deliveries("/internal/chat/deliver")).hasSize(2);
    }

    private void publishMessage(String eventId, Long sendUserId, String text) {
        EdaChatMessage.ChatMessage chatMessage = EdaChatMessage.ChatMessage.newBuilder().setEventId(eventId).setSendUserId(sendUserId)
            .setChannelId(channelId).setMessage(text).setPublishDt(System.currentTimeMillis()).build();
        kafkaTemplate.send("chat-send-message", String.valueOf(channelId), chatMessage.toByteArray()).join();
    }

    private static List<byte[]> deliveries(String path) {
        return RECEIVED.stream().filter(request -> request.path().equals(path)).map(ReceivedRequest::body).toList();
    }

    private String newEventId() {
        String eventId = UUID.randomUUID().toString();
        eventIds.add(eventId);
        return eventId;
    }

    private static HttpServer startChatServer() {
        try {
            HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
            server.setExecutor(Executors.newCachedThreadPool());
            server.createContext("/", exchange -> {
                RECEIVED.add(new ReceivedRequest(exchange.getRequestURI().getPath(), exchange.getRequestBody().readAllBytes()));
                int status = FAILURES_LEFT.getAndUpdate(left -> Math.max(0, left - 1)) > 0 ? 500 : 200;
                exchange.sendResponseHeaders(status, -1);
                exchange.close();
            });
            server.start();
            return server;
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
    }
}
