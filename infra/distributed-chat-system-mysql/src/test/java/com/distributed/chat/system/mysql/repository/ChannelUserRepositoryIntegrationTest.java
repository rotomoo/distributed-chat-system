package com.distributed.chat.system.mysql.repository;

import static org.assertj.core.api.Assertions.assertThat;

import com.distributed.chat.system.mysql.entity.ChannelUser;
import com.distributed.chat.system.mysql.pk.ChannelUserPk;
import jakarta.transaction.Transactional;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

@DisplayName("[통합] ChannelUserRepository 읽음 위치 / 알림 음소거 테스트")
@SpringBootTest
@Transactional
class ChannelUserRepositoryIntegrationTest {

    private static final String OLDER = "6acc66675f99715e286f0f6e";
    private static final String NEWER = "6acc66675f99715e286f0f6f";

    @Autowired
    private ChannelUserRepository channelUserRepository;

    private Long channelId;
    private Long userId;

    @BeforeEach
    void setup() {
        // 실제 데이터와 겹치지 않는 큰 ID (트랜잭션 롤백으로 지워진다)
        channelId = ThreadLocalRandom.current().nextLong(3_000_000_000L, 4_000_000_000L);
        userId = ThreadLocalRandom.current().nextLong(3_000_000_000L, 4_000_000_000L);
    }

    @Test
    @DisplayName("success case : 처음 읽음 처리하면 읽음 위치가 저장된다")
    void 처음_읽음_처리하면_읽음_위치가_저장된다() {
        // given
        saveMember(null, false);

        // when
        int updated = channelUserRepository.moveLastReadMessageIdForward(channelId, userId, OLDER);

        // then
        assertThat(updated).isEqualTo(1);
        assertThat(lastRead()).isEqualTo(OLDER);
    }

    @Test
    @DisplayName("success case : 더 뒤의 메시지로는 옮겨진다")
    void 더_뒤의_메시지로는_옮겨진다() {
        // given
        saveMember(OLDER, false);

        // when
        int updated = channelUserRepository.moveLastReadMessageIdForward(channelId, userId, NEWER);

        // then
        assertThat(updated).isEqualTo(1);
        assertThat(lastRead()).isEqualTo(NEWER);
    }

    @Test
    @DisplayName("fail case : 이전 메시지나 같은 메시지로는 뒤로 가지 않는다")
    void 이전_메시지나_같은_메시지로는_뒤로_가지_않는다() {
        // given
        saveMember(NEWER, false);

        // when
        int backward = channelUserRepository.moveLastReadMessageIdForward(channelId, userId, OLDER);
        int same = channelUserRepository.moveLastReadMessageIdForward(channelId, userId, NEWER);

        // then
        assertThat(backward).isZero();
        assertThat(same).isZero();
        assertThat(lastRead()).isEqualTo(NEWER);
    }

    @Test
    @DisplayName("fail case : 나간 멤버나 멤버가 아니면 갱신되지 않는다")
    void 나간_멤버나_멤버가_아니면_갱신되지_않는다() {
        // given
        saveMember(null, true);

        // when
        int left = channelUserRepository.moveLastReadMessageIdForward(channelId, userId, OLDER);
        int notMember = channelUserRepository.moveLastReadMessageIdForward(channelId, userId + 1, OLDER);

        // then
        assertThat(left).isZero();
        assertThat(notMember).isZero();
        assertThat(lastRead()).isNull();
    }

    @Test
    @DisplayName("success case : 채널 멤버 ID 는 나가지 않은 멤버만 돌려준다")
    void 채널_멤버_ID_는_나가지_않은_멤버만_돌려준다() {
        // given
        saveMember(null, false);
        channelUserRepository.saveAndFlush(ChannelUser.builder().channelId(channelId).userId(userId + 1).deleteYn(false).build());
        channelUserRepository.saveAndFlush(ChannelUser.builder().channelId(channelId).userId(userId + 2).deleteYn(true).build());
        channelUserRepository.saveAndFlush(ChannelUser.builder().channelId(channelId + 1).userId(userId + 3).deleteYn(false).build());

        // when
        List<Long> memberIds = channelUserRepository.findMemberUserIds(channelId);

        // then
        assertThat(memberIds).containsExactlyInAnyOrder(userId, userId + 1);
    }

    @Test
    @DisplayName("success case : 알림 음소거를 켜고 끌 수 있고 기본값은 false 다 (같은 값을 다시 써도 찾은 행 1)")
    void 알림_음소거를_켜고_끌_수_있고_기본값은_false_다() {
        // given
        saveMember(null, false);

        // when
        boolean initial = channelUserRepository.findById(new ChannelUserPk(channelId, userId)).orElseThrow().isNotificationMuted();
        int muted = channelUserRepository.updateNotificationMuted(channelId, userId, true);
        int again = channelUserRepository.updateNotificationMuted(channelId, userId, true);
        boolean afterMute = channelUserRepository.findById(new ChannelUserPk(channelId, userId)).orElseThrow().isNotificationMuted();
        channelUserRepository.updateNotificationMuted(channelId, userId, false);

        // then
        assertThat(initial).isFalse();
        assertThat(muted).isEqualTo(1);
        assertThat(again).isEqualTo(1);
        assertThat(afterMute).isTrue();
        assertThat(channelUserRepository.findById(new ChannelUserPk(channelId, userId)).orElseThrow().isNotificationMuted()).isFalse();
    }

    @Test
    @DisplayName("fail case : 나간 멤버나 멤버가 아니면 알림 음소거가 바뀌지 않는다 (0)")
    void 나간_멤버나_멤버가_아니면_알림_음소거가_바뀌지_않는다() {
        // given
        saveMember(null, true);

        // when & then
        assertThat(channelUserRepository.updateNotificationMuted(channelId, userId, true)).isZero();
        assertThat(channelUserRepository.updateNotificationMuted(channelId, userId + 1, true)).isZero();
    }

    @Test
    @DisplayName("success case : 알림 받을 사람은 넘긴 사람 중 나가지 않았고 음소거하지 않은 이 채널 멤버뿐이다")
    void 알림_받을_사람은_나가지_않았고_음소거하지_않은_이_채널_멤버뿐이다() {
        // given : userId 멤버, +1 음소거, +2 나감, +3 다른 채널 멤버, +4 목록에 없음
        saveMember(null, false);
        channelUserRepository.saveAndFlush(ChannelUser.builder().channelId(channelId).userId(userId + 1).notificationMuted(true).deleteYn(false).build());
        channelUserRepository.saveAndFlush(ChannelUser.builder().channelId(channelId).userId(userId + 2).deleteYn(true).build());
        channelUserRepository.saveAndFlush(ChannelUser.builder().channelId(channelId + 1).userId(userId + 3).deleteYn(false).build());
        channelUserRepository.saveAndFlush(ChannelUser.builder().channelId(channelId).userId(userId + 4).deleteYn(false).build());

        // when
        List<Long> unmuted = channelUserRepository.findUnmutedMemberUserIds(channelId, List.of(userId, userId + 1, userId + 2, userId + 3));

        // then
        assertThat(unmuted).containsExactly(userId);
    }

    private void saveMember(String lastReadMessageId, boolean deleted) {
        channelUserRepository.saveAndFlush(ChannelUser.builder()
            .channelId(channelId)
            .userId(userId)
            .lastReadMessageId(lastReadMessageId)
            .deleteYn(deleted)
            .build());
    }

    private String lastRead() {
        return channelUserRepository.findById(new ChannelUserPk(channelId, userId)).orElseThrow().getLastReadMessageId();
    }
}
