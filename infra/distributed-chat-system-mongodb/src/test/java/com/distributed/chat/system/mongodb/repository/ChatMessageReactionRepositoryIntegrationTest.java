package com.distributed.chat.system.mongodb.repository;

import static org.assertj.core.api.Assertions.assertThat;

import com.distributed.chat.system.mongodb.collection.ChatMessage;
import com.distributed.chat.system.mongodb.collection.ChatMessage.Reaction;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ThreadLocalRandom;
import org.bson.types.ObjectId;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

@DisplayName("[통합] ChatMessageRepository 리액션 원자적 갱신 테스트")
@SpringBootTest
class ChatMessageReactionRepositoryIntegrationTest {

    @Autowired
    private ChatMessageRepository chatMessageRepository;

    private Long channelId;
    private ObjectId messageId;

    @BeforeEach
    void setup() {
        channelId = ThreadLocalRandom.current().nextLong(1_000_000_000_000L, 2_000_000_000_000L);
        ChatMessage message = chatMessageRepository.save(ChatMessage.builder()
            .channelId(channelId).content("리액션 대상").createUserId(1L).eventPublishDt(Instant.now()).createDt(Instant.now())
            .build());
        messageId = message.getId();
    }

    @AfterEach
    void tearDown() {
        chatMessageRepository.deleteById(messageId);
    }

    @Test
    @DisplayName("success case : 처음 누르면 이모지가 생기고 누른 순서대로 쌓인다")
    void 처음_누르면_이모지가_생기고_누른_순서대로_쌓인다() {
        // given
        chatMessageRepository.addReaction(messageId, channelId, "👍", 1L);

        // when
        Optional<List<Reaction>> reactions = chatMessageRepository.addReaction(messageId, channelId, "❤️", 2L);

        // then
        assertThat(reactions).contains(List.of(new Reaction("👍", List.of(1L)), new Reaction("❤️", List.of(2L))));
    }

    @Test
    @DisplayName("success case : 같은 사람이 두 번 눌러도 1명이고 다른 사람은 같은 이모지에 더해진다")
    void 같은_사람이_두_번_눌러도_1명이고_다른_사람은_같은_이모지에_더해진다() {
        // given
        chatMessageRepository.addReaction(messageId, channelId, "👍", 1L);
        chatMessageRepository.addReaction(messageId, channelId, "👍", 1L);

        // when
        Optional<List<Reaction>> reactions = chatMessageRepository.addReaction(messageId, channelId, "👍", 2L);

        // then
        assertThat(reactions).contains(List.of(new Reaction("👍", List.of(1L, 2L))));
    }

    @Test
    @DisplayName("success case : 빼면 그 사람만 빠지고 0명이 된 이모지는 사라지며 두 번 빼도 그대로다")
    void 빼면_그_사람만_빠지고_0명이_된_이모지는_사라지며_두_번_빼도_그대로다() {
        // given
        chatMessageRepository.addReaction(messageId, channelId, "👍", 1L);
        chatMessageRepository.addReaction(messageId, channelId, "👍", 2L);
        chatMessageRepository.addReaction(messageId, channelId, "🎉", 1L);

        // when
        chatMessageRepository.removeReaction(messageId, channelId, "🎉", 1L);
        chatMessageRepository.removeReaction(messageId, channelId, "🎉", 1L);
        Optional<List<Reaction>> reactions = chatMessageRepository.removeReaction(messageId, channelId, "👍", 1L);

        // then
        assertThat(reactions).contains(List.of(new Reaction("👍", List.of(2L))));
    }

    @Test
    @DisplayName("success case : 서로 다른 이모지는 50개까지만 생기고 이미 있는 이모지에는 계속 더할 수 있다")
    void 서로_다른_이모지는_50개까지만_생기고_이미_있는_이모지에는_계속_더할_수_있다() {
        // given
        for (int i = 0; i < 50; i++) {
            chatMessageRepository.addReaction(messageId, channelId, "e" + i, 1L);
        }

        // when
        Optional<List<Reaction>> over = chatMessageRepository.addReaction(messageId, channelId, "e50", 1L);
        Optional<List<Reaction>> existing = chatMessageRepository.addReaction(messageId, channelId, "e0", 2L);

        // then
        assertThat(over).get().asList().hasSize(50);
        assertThat(over.get()).extracting(Reaction::emoji).doesNotContain("e50");
        assertThat(existing.get().get(0)).isEqualTo(new Reaction("e0", List.of(1L, 2L)));
    }

    @Test
    @DisplayName("fail case : 메시지가 없거나 채널이 다르면 아무것도 바꾸지 않고 empty 다")
    void 메시지가_없거나_채널이_다르면_아무것도_바꾸지_않고_empty_다() {
        // when
        Optional<List<Reaction>> otherChannel = chatMessageRepository.addReaction(messageId, channelId + 1, "👍", 1L);
        Optional<List<Reaction>> missing = chatMessageRepository.addReaction(new ObjectId(), channelId, "👍", 1L);
        Optional<List<Reaction>> removeOtherChannel = chatMessageRepository.removeReaction(messageId, channelId + 1, "👍", 1L);

        // then
        assertThat(otherChannel).isEmpty();
        assertThat(missing).isEmpty();
        assertThat(removeOtherChannel).isEmpty();
        assertThat(chatMessageRepository.findById(messageId).orElseThrow().getReactions()).isNull();
    }
}
