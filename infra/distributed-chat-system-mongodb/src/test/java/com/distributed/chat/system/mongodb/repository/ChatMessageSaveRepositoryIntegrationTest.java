package com.distributed.chat.system.mongodb.repository;

import static org.assertj.core.api.Assertions.assertThat;

import com.distributed.chat.system.mongodb.collection.ChatMessage;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;
import org.bson.Document;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;

@DisplayName("[통합] ChatMessageRepository eventId 한 번만 저장 테스트")
@SpringBootTest
class ChatMessageSaveRepositoryIntegrationTest {

    @Autowired
    private ChatMessageRepository chatMessageRepository;

    @Autowired
    @Qualifier("notificationMongoTemplate")
    private MongoTemplate mongoTemplate;

    private final Long channelId = ThreadLocalRandom.current().nextLong(1_000_000_000_000L, 2_000_000_000_000L);
    private final List<String> eventIds = new ArrayList<>();

    @AfterEach
    void tearDown() {
        mongoTemplate.remove(Query.query(Criteria.where("eventId").in(eventIds)), ChatMessage.class);
    }

    @Test
    @DisplayName("success case : 처음 저장하면 MongoDB 가 _id 를 만들고 모든 필드를 그대로 저장한다")
    void 처음_저장하면_MongoDB_가_id_를_만들고_모든_필드를_그대로_저장한다() {
        // given
        String eventId = newEventId();

        // when
        ChatMessage saved = chatMessageRepository.saveOnce(message(eventId, "안녕",
            List.of(new ChatMessage.Attachment("6acc66675f99715e286f0f6e", "사진.png", "image/png", 10L))));

        // then
        assertThat(saved.getId()).isNotNull();
        ChatMessage found = chatMessageRepository.findById(saved.getId()).orElseThrow();
        assertThat(found.getEventId()).isEqualTo(eventId);
        assertThat(found.getChannelId()).isEqualTo(channelId);
        assertThat(found.getContent()).isEqualTo("안녕");
        assertThat(found.getCreateUserId()).isEqualTo(5L);
        assertThat(found.getAttachments()).containsExactly(new ChatMessage.Attachment("6acc66675f99715e286f0f6e", "사진.png", "image/png", 10L));
        assertThat(chatMessageRepository.findByEventId(eventId)).map(ChatMessage::getId).contains(saved.getId());
    }

    @Test
    @DisplayName("success case : 같은 eventId 로 다시 저장하면 새로 넣지 않고 처음 문서(같은 _id, 처음 내용)를 돌려준다")
    void 같은_eventId_로_다시_저장하면_새로_넣지_않고_처음_문서를_돌려준다() {
        // given
        String eventId = newEventId();
        ChatMessage first = chatMessageRepository.saveOnce(message(eventId, "처음", null));

        // when
        ChatMessage again = chatMessageRepository.saveOnce(message(eventId, "두번째", null));

        // then
        assertThat(again.getId()).isEqualTo(first.getId());
        assertThat(again.getContent()).isEqualTo("처음");
        assertThat(mongoTemplate.count(Query.query(Criteria.where("eventId").is(eventId)), ChatMessage.class)).isEqualTo(1);
    }

    @Test
    @DisplayName("success case : 같은 eventId 를 동시에 저장해도 문서는 하나이고 모두 같은 _id 를 받는다")
    void 같은_eventId_를_동시에_저장해도_문서는_하나이고_모두_같은_id_를_받는다() throws Exception {
        // given
        String eventId = newEventId();
        ExecutorService executor = Executors.newFixedThreadPool(8);
        CountDownLatch start = new CountDownLatch(1);
        List<Future<ChatMessage>> futures = new ArrayList<>();
        for (int i = 0; i < 8; i++) {
            Callable<ChatMessage> save = () -> {
                start.await();
                return chatMessageRepository.saveOnce(message(eventId, "동시", null));
            };
            futures.add(executor.submit(save));
        }

        // when
        start.countDown();
        List<ChatMessage> results = new ArrayList<>();
        for (Future<ChatMessage> future : futures) {
            results.add(future.get(10, TimeUnit.SECONDS));
        }
        executor.shutdown();

        // then
        assertThat(results).extracting(ChatMessage::getId).containsOnly(results.get(0).getId());
        assertThat(mongoTemplate.count(Query.query(Criteria.where("eventId").is(eventId)), ChatMessage.class)).isEqualTo(1);
    }

    @Test
    @DisplayName("success case : eventId 유니크 인덱스가 있다 (eventId 없는 예전 문서는 sparse 로 제외)")
    void eventId_유니크_인덱스가_있다() {
        // when
        List<Document> indexes = mongoTemplate.getCollection("chatMessage").listIndexes().into(new ArrayList<>());

        // then
        Document eventIdIndex = indexes.stream().filter(index -> "ux_event_id".equals(index.getString("name"))).findFirst().orElseThrow();
        assertThat(eventIdIndex.getBoolean("unique")).isTrue();
        assertThat(eventIdIndex.getBoolean("sparse")).isTrue();
    }

    private String newEventId() {
        String eventId = UUID.randomUUID().toString();
        eventIds.add(eventId);
        return eventId;
    }

    private ChatMessage message(String eventId, String content, List<ChatMessage.Attachment> attachments) {
        return ChatMessage.builder().eventId(eventId).channelId(channelId).content(content).createUserId(5L)
            .eventPublishDt(Instant.now()).createDt(Instant.now()).attachments(attachments).build();
    }
}
