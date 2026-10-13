package com.distributed.chat.system.mongodb.repository;

import static org.assertj.core.api.Assertions.assertThat;

import com.distributed.chat.system.mongodb.collection.ChatMessage;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ThreadLocalRandom;
import org.bson.types.ObjectId;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.domain.Slice;

@DisplayName("[통합] ChatMessageRepository Querydsl 조회 (최근 / before / after / 안 읽은 메시지) 테스트")
@SpringBootTest
class ChatMessageQueryDslIntegrationTest {

    private static final Long ME = 1L;
    private static final Long OTHER = 2L;

    @Autowired
    private ChatMessageRepository chatMessageRepository;

    private Long channelId;
    private final List<ChatMessage> saved = new ArrayList<>();

    @BeforeEach
    void setup() {
        // 다른 테스트/실데이터와 겹치지 않는 채널
        channelId = ThreadLocalRandom.current().nextLong(1_000_000_000_000L, 2_000_000_000_000L);
    }

    @AfterEach
    void tearDown() {
        chatMessageRepository.deleteAll(saved);
    }

    @Test
    @DisplayName("success case : 읽음 위치가 없으면 채널의 남이 보낸 메시지를 모두 센다")
    void 읽음_위치가_없으면_채널의_남이_보낸_메시지를_모두_센다() {
        // given
        save(OTHER, "a");
        save(ME, "내 메시지");
        save(OTHER, "b");
        saveInOtherChannel(OTHER, "다른 채널");

        // when
        long unread = chatMessageRepository.countUnread(channelId, null, ME, 0);

        // then
        assertThat(unread).isEqualTo(2);
    }

    @Test
    @DisplayName("success case : 읽음 위치보다 뒤의 남이 보낸 메시지만 센다")
    void 읽음_위치보다_뒤의_남이_보낸_메시지만_센다() {
        // given
        save(OTHER, "a");
        ChatMessage lastRead = save(OTHER, "b");
        save(ME, "내 메시지");
        save(OTHER, "c");

        // when
        long unread = chatMessageRepository.countUnread(channelId, lastRead.getId(), ME, 0);

        // then
        assertThat(unread).isEqualTo(1);
    }

    @Test
    @DisplayName("success case : limit 을 주면 그 수까지만 센다")
    void limit_을_주면_그_수까지만_센다() {
        // given
        for (int i = 0; i < 3; i++) {
            save(OTHER, "m" + i);
        }

        // when
        long unread = chatMessageRepository.countUnread(channelId, null, ME, 2);

        // then
        assertThat(unread).isEqualTo(2);
    }

    @Test
    @DisplayName("success case : 안 읽은 첫 메시지는 읽음 위치 이후 남이 보낸 가장 오래된 메시지다")
    void 안_읽은_첫_메시지는_읽음_위치_이후_남이_보낸_가장_오래된_메시지다() {
        // given
        ChatMessage lastRead = save(OTHER, "읽음");
        save(ME, "내 메시지");
        save(OTHER, "1");
        save(OTHER, "2");

        // when
        Optional<ChatMessage> firstUnread = chatMessageRepository.findFirstUnread(channelId, lastRead.getId(), ME);

        // then
        assertThat(firstUnread).map(ChatMessage::getContent).contains("1");
    }

    @Test
    @DisplayName("success case : 안 읽은 메시지가 없으면 첫 메시지도 없다")
    void 안_읽은_메시지가_없으면_첫_메시지도_없다() {
        // given
        ChatMessage lastRead = save(OTHER, "읽음");
        save(ME, "내 메시지");

        // when
        Optional<ChatMessage> firstUnread = chatMessageRepository.findFirstUnread(channelId, lastRead.getId(), ME);

        // then
        assertThat(firstUnread).isEmpty();
    }

    @Test
    @DisplayName("success case : after 보다 뒤의 메시지를 오래된 순으로 가져온다")
    void after_보다_뒤의_메시지를_오래된_순으로_가져온다() {
        // given
        save(OTHER, "1");
        ChatMessage after = save(ME, "2");
        save(OTHER, "3");
        save(ME, "4");
        save(OTHER, "5");

        // when
        Slice<ChatMessage> messages = chatMessageRepository.findMessagesAfter(channelId, after.getId(), 2);

        // then
        assertThat(messages.getContent()).extracting(ChatMessage::getContent).containsExactly("3", "4");
        assertThat(messages.hasNext()).isTrue();
    }

    @Test
    @DisplayName("success case : 최근 메시지를 최신순으로 size 개 가져오고 다른 채널 메시지는 섞이지 않는다")
    void 최근_메시지를_최신순으로_size_개_가져오고_다른_채널_메시지는_섞이지_않는다() {
        // given
        save(OTHER, "1");
        save(ME, "2");
        save(OTHER, "3");
        saveInOtherChannel(OTHER, "다른 채널");

        // when
        Slice<ChatMessage> messages = chatMessageRepository.findLatestMessages(channelId, 2);

        // then
        assertThat(messages.getContent()).extracting(ChatMessage::getContent).containsExactly("3", "2");
        assertThat(messages.hasNext()).isTrue();
    }

    @Test
    @DisplayName("success case : before 보다 이전 메시지를 최신순으로 가져온다")
    void before_보다_이전_메시지를_최신순으로_가져온다() {
        // given
        save(OTHER, "1");
        save(ME, "2");
        save(OTHER, "3");
        ChatMessage before = save(ME, "4");
        save(OTHER, "5");

        // when
        Slice<ChatMessage> messages = chatMessageRepository.findMessagesBefore(channelId, before.getId(), 2);

        // then
        assertThat(messages.getContent()).extracting(ChatMessage::getContent).containsExactly("3", "2");
        assertThat(messages.hasNext()).isTrue();
    }

    @Test
    @DisplayName("success case : 남은 메시지가 딱 size 개면 hasNext 는 false, size + 1 개면 true 다 (최근 / before / after)")
    void 남은_메시지가_딱_size_개면_hasNext_는_false_size_더하기_1_개면_true_다() {
        // given : 1 2 3 4 5
        ChatMessage first = save(OTHER, "1");
        save(OTHER, "2");
        ChatMessage third = save(OTHER, "3");
        save(OTHER, "4");
        ChatMessage fifth = save(OTHER, "5");

        // when
        Slice<ChatMessage> latestExact = chatMessageRepository.findLatestMessages(channelId, 5);
        Slice<ChatMessage> latestMore = chatMessageRepository.findLatestMessages(channelId, 4);
        Slice<ChatMessage> beforeExact = chatMessageRepository.findMessagesBefore(channelId, third.getId(), 2);
        Slice<ChatMessage> beforeMore = chatMessageRepository.findMessagesBefore(channelId, fifth.getId(), 3);
        Slice<ChatMessage> afterExact = chatMessageRepository.findMessagesAfter(channelId, third.getId(), 2);
        Slice<ChatMessage> afterMore = chatMessageRepository.findMessagesAfter(channelId, first.getId(), 3);

        // then : 최근 / before 는 더 이전이, after 는 더 최신이 남았는지
        assertThat(latestExact.hasNext()).isFalse();
        assertThat(latestExact.getContent()).extracting(ChatMessage::getContent).containsExactly("5", "4", "3", "2", "1");
        assertThat(latestMore.hasNext()).isTrue();
        assertThat(latestMore.getContent()).extracting(ChatMessage::getContent).containsExactly("5", "4", "3", "2");
        assertThat(beforeExact.hasNext()).isFalse();
        assertThat(beforeExact.getContent()).extracting(ChatMessage::getContent).containsExactly("2", "1");
        assertThat(beforeMore.hasNext()).isTrue();
        assertThat(beforeMore.getContent()).extracting(ChatMessage::getContent).containsExactly("4", "3", "2");
        assertThat(afterExact.hasNext()).isFalse();
        assertThat(afterExact.getContent()).extracting(ChatMessage::getContent).containsExactly("4", "5");
        assertThat(afterMore.hasNext()).isTrue();
        assertThat(afterMore.getContent()).extracting(ChatMessage::getContent).containsExactly("2", "3", "4");
    }

    @Test
    @DisplayName("success case : 그 채널의 메시지만 존재한다고 본다")
    void 그_채널의_메시지만_존재한다고_본다() {
        // given
        ChatMessage message = save(OTHER, "1");

        // when & then
        assertThat(chatMessageRepository.existsInChannel(message.getId(), channelId)).isTrue();
        assertThat(chatMessageRepository.existsInChannel(message.getId(), channelId + 1)).isFalse();
        assertThat(chatMessageRepository.existsInChannel(new ObjectId(), channelId)).isFalse();
    }

    @Test
    @DisplayName("success case : 여러 채널의 안 읽은 수를 한 번에 채널마다 기준 이후 남의 메시지로 cap 까지 센다")
    void 여러_채널의_안_읽은_수를_한_번에_채널마다_기준_이후_남의_메시지로_cap_까지_센다() {
        // given : A = 기준 없음, 남 2 + 나 1 / B = 기준 이후 남 1 / C = 남 5 (cap 3) / D = 전부 읽음
        Long a = channelId;
        Long b = channelId + 10;
        Long c = channelId + 20;
        Long d = channelId + 30;
        saveTo(a, OTHER, "a1");
        saveTo(a, ME, "a 내 것");
        saveTo(a, OTHER, "a2");
        saveTo(b, OTHER, "b 읽은 것");
        ChatMessage bRead = saveTo(b, OTHER, "b 여기까지 읽음");
        saveTo(b, OTHER, "b1");
        for (int i = 0; i < 5; i++) {
            saveTo(c, OTHER, "c" + i);
        }
        ChatMessage dRead = saveTo(d, OTHER, "d 다 읽음");
        Map<Long, ObjectId> unreadAfter = new LinkedHashMap<>();
        unreadAfter.put(a, null);
        unreadAfter.put(b, bRead.getId());
        unreadAfter.put(c, null);
        unreadAfter.put(d, dRead.getId());

        // when
        Map<Long, Integer> unread = chatMessageRepository.countUnreadByChannel(unreadAfter, ME, 3);

        // then
        assertThat(unread).isEqualTo(Map.of(a, 2, b, 1, c, 3));
        assertThat(chatMessageRepository.countUnreadByChannel(Map.of(c, saved.get(6).getId()), ME, 0)).isEqualTo(Map.of(c, 4));
        assertThat(chatMessageRepository.countUnreadByChannel(Map.of(), ME, 3)).isEmpty();
    }

    @Test
    @DisplayName("success case : limit 0 이면 끝까지 센다")
    void limit_0_이면_끝까지_센다() {
        // given
        for (int i = 0; i < 3; i++) {
            save(OTHER, "m" + i);
        }

        // when
        long unread = chatMessageRepository.countUnread(channelId, null, ME, 0);

        // then
        assertThat(unread).isEqualTo(3);
    }

    private ChatMessage save(Long createUserId, String content) {
        return saveTo(channelId, createUserId, content);
    }

    private void saveInOtherChannel(Long createUserId, String content) {
        saveTo(channelId + 1, createUserId, content);
    }

    private ChatMessage saveTo(Long targetChannelId, Long createUserId, String content) {
        ChatMessage message = chatMessageRepository.save(ChatMessage.builder()
            .channelId(targetChannelId)
            .content(content)
            .createUserId(createUserId)
            .eventPublishDt(Instant.now())
            .createDt(Instant.now())
            .build());
        saved.add(message);
        return message;
    }
}
