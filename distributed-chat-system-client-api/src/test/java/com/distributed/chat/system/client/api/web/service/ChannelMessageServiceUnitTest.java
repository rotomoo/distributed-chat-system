package com.distributed.chat.system.client.api.web.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowableOfType;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.distributed.chat.system.client.api.web.dto.channel.CatchUpDto;
import com.distributed.chat.system.client.api.web.dto.channel.ChannelMessageDto;
import com.distributed.chat.system.common.exception.ApiException;
import com.distributed.chat.system.common.exception.ErrorCode;
import com.distributed.chat.system.common.response.ResponseData;
import com.distributed.chat.system.mongodb.collection.ChatMessage;
import com.distributed.chat.system.mongodb.repository.ChatMessageRepository;
import com.distributed.chat.system.mysql.entity.ChannelUser;
import com.distributed.chat.system.mysql.repository.ChannelUserRepository;
import java.time.Instant;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.bson.types.ObjectId;
import org.junit.jupiter.api.AfterEach;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Slice;
import org.springframework.data.domain.SliceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.security.core.context.SecurityContextHolder;

@DisplayName("[단위] ChannelMessageService 테스트")
@ExtendWith(MockitoExtension.class)
class ChannelMessageServiceUnitTest {

    private static final Long LOGIN_ID = 5L;
    private static final Long CHANNEL_ID = 7L;
    private static final String LAST_READ = "000000000000000000000002";

    private static final Instant JOINED_AT = Instant.parse("2026-10-01T00:00:00Z");

    private static final Instant SENT_AT = Instant.ofEpochMilli(1_700_000_000_000L);

    @Mock
    private ChatMessageRepository chatMessageRepository;

    @Mock
    private ChannelUserRepository channelUserRepository;

    private ChannelMessageService channelMessageService;

    @BeforeEach
    void setup() {
        // 멤버 확인은 실제 ChannelMemberGuard 로 (channelUserRepository 목을 그대로 쓴다)
        channelMessageService = new ChannelMessageService(chatMessageRepository, channelUserRepository,
            new ChannelMemberGuard(channelUserRepository));
        SecurityContextHolder.getContext().setAuthentication(
            new UsernamePasswordAuthenticationToken(String.valueOf(LOGIN_ID), null, List.of()));
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    // ── 메시지 조회 ──────────────────────────────────────────────

    @Test
    @DisplayName("success case : 최신 메시지를 가져와 오래된 순으로 돌려준다")
    void 최신_메시지를_가져와_오래된_순으로_돌려준다() {
        // arrange
        givenChannelMember();
        when(chatMessageRepository.findLatestMessages(CHANNEL_ID, 2))
            .thenReturn(slice(2, true, message("000000000000000000000003", "세번째"), message("000000000000000000000002", "두번째")));

        // act
        ResponseData<Map<String, List<ChannelMessageDto>>> response = channelMessageService.getMessages(CHANNEL_ID, null, null, 2);

        // assert
        assertThat(response.getData().get("list")).extracting(ChannelMessageDto::getMessage).containsExactly("두번째", "세번째");
        assertThat(hasNext(response)).isTrue();
    }

    @Test
    @DisplayName("success case : 메시지 항목에 첨부와 리액션을 담고 없으면 빈 배열이다")
    void 메시지_항목에_첨부와_리액션을_담고_없으면_빈_배열이다() {
        // arrange
        givenChannelMember();
        ChatMessage withExtras = ChatMessage.builder().id(new ObjectId("6acc66675f99715e286f0f6f")).channelId(CHANNEL_ID).content("")
            .createUserId(LOGIN_ID).eventPublishDt(SENT_AT)
            .attachments(List.of(new ChatMessage.Attachment("6acc66675f99715e286f0f6e", "사진.png", "image/png", 10L)))
            .reactions(List.of(new ChatMessage.Reaction("👍", List.of(1L, 5L))))
            .build();
        when(chatMessageRepository.findLatestMessages(CHANNEL_ID, 20))
            .thenReturn(slice(20, false, withExtras, message("000000000000000000000001", "그냥 글")));

        // act
        ResponseData<Map<String, List<ChannelMessageDto>>> response = channelMessageService.getMessages(CHANNEL_ID, null, null, 20);

        // assert
        List<ChannelMessageDto> messages = response.getData().get("list");
        assertThat(messages).extracting(ChannelMessageDto::getMessageId)
            .containsExactly("000000000000000000000001", "6acc66675f99715e286f0f6f"); // ObjectId → 소문자 24자리 hex
        assertThat(messages.get(0).getAttachments()).isEmpty();
        assertThat(messages.get(0).getReactions()).isEmpty();
        assertThat(messages.get(1).getAttachments()).singleElement().satisfies(attachment -> {
            assertThat(attachment.getFileId()).isEqualTo("6acc66675f99715e286f0f6e");
            assertThat(attachment.getName()).isEqualTo("사진.png");
            assertThat(attachment.getContentType()).isEqualTo("image/png");
            assertThat(attachment.getSize()).isEqualTo(10L);
        });
        assertThat(messages.get(1).getReactions()).singleElement().satisfies(reaction -> {
            assertThat(reaction.getEmoji()).isEqualTo("👍");
            assertThat(reaction.getUserIds()).containsExactly(1L, 5L);
        });
    }

    @Test
    @DisplayName("success case : before 를 주면 그보다 이전 메시지를 가져온다")
    void before_를_주면_그보다_이전_메시지를_가져온다() {
        // arrange
        givenChannelMember();
        when(chatMessageRepository.findMessagesBefore(CHANNEL_ID, new ObjectId("000000000000000000000002"), 50))
            .thenReturn(slice(50, false, message("000000000000000000000001", "첫번째")));

        // act
        ResponseData<Map<String, List<ChannelMessageDto>>> response =
            channelMessageService.getMessages(CHANNEL_ID, "000000000000000000000002", null, 50);

        // assert
        assertThat(response.getData().get("list")).extracting(ChannelMessageDto::getMessage).containsExactly("첫번째");
        assertThat(hasNext(response)).isFalse();
    }

    @Test
    @DisplayName("success case : after 를 주면 그보다 뒤의 메시지를 오래된 순 그대로 돌려준다")
    void after_를_주면_그보다_뒤의_메시지를_오래된_순_그대로_돌려준다() {
        // arrange
        givenChannelMember();
        when(chatMessageRepository.findMessagesAfter(
            CHANNEL_ID, new ObjectId("000000000000000000000001"), 100))
            .thenReturn(slice(100, true, message("000000000000000000000002", "두번째"), message("000000000000000000000003", "세번째")));

        // act
        ResponseData<Map<String, List<ChannelMessageDto>>> response =
            channelMessageService.getMessages(CHANNEL_ID, null, "000000000000000000000001", 500);

        // assert
        assertThat(response.getData().get("list")).extracting(ChannelMessageDto::getMessage).containsExactly("두번째", "세번째");
        assertThat(hasNext(response)).isTrue();
        verify(chatMessageRepository, never()).findLatestMessages(anyLong(), anyInt());
    }

    @Test
    @DisplayName("fail case : before 와 after 를 같이 주면 거절한다")
    void before_와_after_를_같이_주면_거절한다() {
        // arrange
        givenChannelMember();

        // act
        ApiException exception = catchThrowableOfType(() -> channelMessageService.getMessages(
            CHANNEL_ID, "000000000000000000000003", "000000000000000000000001", 20), ApiException.class);

        // assert
        assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.INVALID_MESSAGE_CURSOR);
        verifyNoInteractions(chatMessageRepository);
    }

    @Test
    @DisplayName("fail case : 채널 멤버가 아니면 메시지를 볼 수 없다")
    void 채널_멤버가_아니면_메시지를_볼_수_없다() {
        // arrange
        when(channelUserRepository.existsByChannelIdAndUserIdAndDeleteYnIsFalse(anyLong(), anyLong())).thenReturn(false);

        // act
        ApiException exception = catchThrowableOfType(() -> channelMessageService.getMessages(CHANNEL_ID, null, null, 50), ApiException.class);

        // assert
        assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.NOT_CHANNEL_MEMBER);
        verifyNoInteractions(chatMessageRepository);
    }

    @Test
    @DisplayName("fail case : before 가 메시지 ID 형식이 아니면 거절한다")
    void before_가_메시지_ID_형식이_아니면_거절한다() {
        // arrange
        givenChannelMember();

        // act
        ApiException exception = catchThrowableOfType(() -> channelMessageService.getMessages(CHANNEL_ID, "abc", null, 50), ApiException.class);

        // assert
        assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.INVALID_MESSAGE_CURSOR);
    }

    @Test
    @DisplayName("fail case : after 가 메시지 ID 형식이 아니면 거절한다")
    void after_가_메시지_ID_형식이_아니면_거절한다() {
        // arrange
        givenChannelMember();

        // act
        ApiException exception = catchThrowableOfType(() -> channelMessageService.getMessages(CHANNEL_ID, null, "abc", 50), ApiException.class);

        // assert
        assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.INVALID_MESSAGE_CURSOR);
        verifyNoInteractions(chatMessageRepository);
    }

    // ── 읽음 처리 ────────────────────────────────────────────────

    @Test
    @DisplayName("success case : 읽음 처리는 그 채널의 메시지인지 확인한 뒤 소문자 메시지 ID 로 앞으로만 옮기는 갱신을 요청한다")
    void 읽음_처리는_그_채널의_메시지인지_확인한_뒤_소문자_메시지_ID_로_앞으로만_옮기는_갱신을_요청한다() {
        // arrange
        when(chatMessageRepository.existsInChannel(new ObjectId("6acc66675f99715e286f0f6e"), CHANNEL_ID)).thenReturn(true);
        when(channelUserRepository.moveLastReadMessageIdForward(CHANNEL_ID, LOGIN_ID, "6acc66675f99715e286f0f6e")).thenReturn(1);

        // act
        ResponseData<?> response = channelMessageService.markRead(CHANNEL_ID, "6ACC66675F99715E286F0F6E");

        // assert
        assertThat(response.isSuccess()).isTrue();
        assertThat(response.getData()).isNull();
        verify(channelUserRepository, never()).existsByChannelIdAndUserIdAndDeleteYnIsFalse(anyLong(), anyLong());
    }

    @Test
    @DisplayName("success case : 이미 더 뒤까지 읽었으면 갱신 없이 성공한다")
    void 이미_더_뒤까지_읽었으면_갱신_없이_성공한다() {
        // arrange
        when(chatMessageRepository.existsInChannel(new ObjectId(LAST_READ), CHANNEL_ID)).thenReturn(true);
        when(channelUserRepository.moveLastReadMessageIdForward(CHANNEL_ID, LOGIN_ID, LAST_READ)).thenReturn(0);
        givenChannelMember();

        // act
        ResponseData<?> response = channelMessageService.markRead(CHANNEL_ID, LAST_READ);

        // assert
        assertThat(response.isSuccess()).isTrue();
    }

    @Test
    @DisplayName("fail case : 채널 멤버가 아니면 읽음 처리할 수 없다")
    void 채널_멤버가_아니면_읽음_처리할_수_없다() {
        // arrange
        when(chatMessageRepository.existsInChannel(new ObjectId(LAST_READ), CHANNEL_ID)).thenReturn(true);
        when(channelUserRepository.moveLastReadMessageIdForward(CHANNEL_ID, LOGIN_ID, LAST_READ)).thenReturn(0);
        when(channelUserRepository.existsByChannelIdAndUserIdAndDeleteYnIsFalse(CHANNEL_ID, LOGIN_ID)).thenReturn(false);

        // act
        ApiException exception = catchThrowableOfType(() -> channelMessageService.markRead(CHANNEL_ID, LAST_READ), ApiException.class);

        // assert
        assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.NOT_CHANNEL_MEMBER);
    }

    @Test
    @DisplayName("fail case : 그 채널에 없는 메시지(없는 ID, 다른 채널 메시지, ffff...)로는 읽음 위치를 옮기지 않는다")
    void 그_채널에_없는_메시지로는_읽음_위치를_옮기지_않는다() {
        // arrange
        when(chatMessageRepository.existsInChannel(new ObjectId("ffffffffffffffffffffffff"), CHANNEL_ID)).thenReturn(false);

        // act
        ApiException exception = catchThrowableOfType(
            () -> channelMessageService.markRead(CHANNEL_ID, "ffffffffffffffffffffffff"), ApiException.class);

        // assert
        assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.INVALID_MESSAGE_CURSOR);
        verifyNoInteractions(channelUserRepository);
    }

    @Test
    @DisplayName("fail case : 메시지 ID 형식이 아니거나 없으면 읽음 처리를 거절한다")
    void 메시지_ID_형식이_아니거나_없으면_읽음_처리를_거절한다() {
        // act
        ApiException invalid = catchThrowableOfType(() -> channelMessageService.markRead(CHANNEL_ID, "abc"), ApiException.class);
        ApiException missing = catchThrowableOfType(() -> channelMessageService.markRead(CHANNEL_ID, null), ApiException.class);

        // assert
        assertThat(invalid.getErrorCode()).isEqualTo(ErrorCode.INVALID_MESSAGE_CURSOR);
        assertThat(missing.getErrorCode()).isEqualTo(ErrorCode.INVALID_MESSAGE_CURSOR);
        verifyNoInteractions(channelUserRepository, chatMessageRepository);
    }

    // ── 안 읽은 기준 / 목록용 안 읽은 수 ──────────────────────────

    @Test
    @DisplayName("success case : 한 번도 읽음 처리를 안 했으면 채널에 들어온 시각 이후가 안 읽은 것이다")
    void 한_번도_읽음_처리를_안_했으면_채널에_들어온_시각_이후가_안_읽은_것이다() {
        // arrange
        ChannelUser membership = membership(null, JOINED_AT);

        // act
        ObjectId unreadAfter = ChannelMessageService.unreadAfter(membership);

        // assert
        assertThat(unreadAfter).isEqualTo(ObjectId.getSmallestWithDate(Date.from(JOINED_AT)));
    }

    @Test
    @DisplayName("success case : 읽음 위치가 들어온 시각보다 뒤면 읽음 위치, 앞이면 들어온 시각이 기준이다")
    void 읽음_위치가_들어온_시각보다_뒤면_읽음_위치_앞이면_들어온_시각이_기준이다() {
        // arrange
        ObjectId readAfterJoin = ObjectId.getSmallestWithDate(Date.from(JOINED_AT.plusSeconds(60)));
        ObjectId readBeforeJoin = ObjectId.getSmallestWithDate(Date.from(JOINED_AT.minusSeconds(60)));

        // act & assert
        assertThat(ChannelMessageService.unreadAfter(membership(readAfterJoin.toHexString(), JOINED_AT))).isEqualTo(readAfterJoin);
        assertThat(ChannelMessageService.unreadAfter(membership(readBeforeJoin.toHexString(), JOINED_AT)))
            .isEqualTo(ObjectId.getSmallestWithDate(Date.from(JOINED_AT)));
        assertThat(ChannelMessageService.unreadAfter(membership(LAST_READ, null))).isEqualTo(new ObjectId(LAST_READ));
    }

    @Test
    @DisplayName("success case : 목록용 안 읽은 수는 채널마다 기준을 정해 집계 한 번으로 cap 까지 센다")
    void 목록용_안_읽은_수는_채널마다_기준을_정해_집계_한_번으로_cap_까지_센다() {
        // arrange
        ChannelUser read = ChannelUser.builder().channelId(7L).userId(LOGIN_ID).lastReadMessageId(LAST_READ).deleteYn(false).build();
        ChannelUser neverRead = membership(null, JOINED_AT);
        ReflectionTestUtils.setField(neverRead, "channelId", 8L);
        Map<Long, ObjectId> expected = new java.util.LinkedHashMap<>();
        expected.put(7L, new ObjectId(LAST_READ));
        expected.put(8L, ObjectId.getSmallestWithDate(Date.from(JOINED_AT)));
        when(chatMessageRepository.countUnreadByChannel(expected, LOGIN_ID, 100)).thenReturn(Map.of(7L, 100, 8L, 2));

        // act
        Map<Long, Integer> unread = channelMessageService.countUnread(List.of(read, neverRead), LOGIN_ID, 100);

        // assert
        assertThat(unread).isEqualTo(Map.of(7L, 100, 8L, 2));
    }

    @Test
    @DisplayName("success case : 참여한 채널이 없으면 MongoDB 를 부르지 않는다")
    void 참여한_채널이_없으면_MongoDB_를_부르지_않는다() {
        // act
        Map<Long, Integer> unread = channelMessageService.countUnread(List.of(), LOGIN_ID, 100);

        // assert
        assertThat(unread).isEmpty();
        verifyNoInteractions(chatMessageRepository);
    }

    // ── 따라잡기 (catch-up) ─────────────────────────────────────

    @Test
    @DisplayName("fail case : 채널 멤버가 아니면 따라잡기를 볼 수 없다")
    void 채널_멤버가_아니면_따라잡기를_볼_수_없다() {
        // arrange
        when(channelUserRepository.findByChannelIdAndUserIdAndDeleteYnIsFalse(CHANNEL_ID, LOGIN_ID)).thenReturn(Optional.empty());

        // act
        ApiException exception = catchThrowableOfType(() -> channelMessageService.getCatchUp(CHANNEL_ID), ApiException.class);

        // assert
        assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.NOT_CHANNEL_MEMBER);
        verifyNoInteractions(chatMessageRepository);
    }

    @Test
    @DisplayName("success case : 안 읽은 메시지가 없으면 0 과 null 을 돌려주고 첫 메시지는 찾지 않는다")
    void 안_읽은_메시지가_없으면_0_과_null_을_돌려주고_첫_메시지는_찾지_않는다() {
        // arrange
        givenMembership(LAST_READ);
        when(chatMessageRepository.countUnread(CHANNEL_ID, new ObjectId(LAST_READ), LOGIN_ID, 0)).thenReturn(0L);

        // act
        CatchUpDto catchUp = catchUp();

        // assert
        assertThat(catchUp.getUnreadCount()).isZero();
        assertThat(catchUp.getFirstUnreadMessageId()).isNull();
        verify(chatMessageRepository, never()).findFirstUnread(anyLong(), any(), anyLong());
    }

    @Test
    @DisplayName("success case : 내 읽음 위치 기준으로 상한 없이 센 안 읽은 수와 안 읽은 첫 메시지를 돌려준다")
    void 내_읽음_위치_기준으로_상한_없이_센_안_읽은_수와_안_읽은_첫_메시지를_돌려준다() {
        // arrange
        givenMembership(LAST_READ);
        when(chatMessageRepository.countUnread(CHANNEL_ID, new ObjectId(LAST_READ), LOGIN_ID, 0)).thenReturn(350L);
        when(chatMessageRepository.findFirstUnread(CHANNEL_ID, new ObjectId(LAST_READ), LOGIN_ID))
            .thenReturn(Optional.of(message("6acc66675f99715e286f0f6e", "첫 안 읽음")));

        // act
        CatchUpDto catchUp = catchUp();

        // assert
        assertThat(catchUp.getUnreadCount()).isEqualTo(350);
        assertThat(catchUp.getFirstUnreadMessageId()).isEqualTo("6acc66675f99715e286f0f6e");
    }

    @Test
    @DisplayName("success case : 한 번도 읽음 처리를 안 했으면 채널에 들어온 뒤의 메시지만 안 읽은 것으로 본다")
    void 한_번도_읽음_처리를_안_했으면_채널에_들어온_뒤의_메시지만_안_읽은_것으로_본다() {
        // arrange
        ObjectId joined = ObjectId.getSmallestWithDate(Date.from(JOINED_AT));
        when(channelUserRepository.findByChannelIdAndUserIdAndDeleteYnIsFalse(CHANNEL_ID, LOGIN_ID))
            .thenReturn(Optional.of(membership(null, JOINED_AT)));
        when(chatMessageRepository.countUnread(CHANNEL_ID, joined, LOGIN_ID, 0)).thenReturn(2L);
        when(chatMessageRepository.findFirstUnread(CHANNEL_ID, joined, LOGIN_ID))
            .thenReturn(Optional.of(message("000000000000000000000001", "첫 메시지")));

        // act
        CatchUpDto catchUp = catchUp();

        // assert
        assertThat(catchUp.getUnreadCount()).isEqualTo(2);
        assertThat(catchUp.getFirstUnreadMessageId()).isEqualTo("000000000000000000000001");
    }

    private CatchUpDto catchUp() {
        ResponseData<CatchUpDto> response = channelMessageService.getCatchUp(CHANNEL_ID);
        return response.getData();
    }

    private void givenMembership(String lastReadMessageId) {
        when(channelUserRepository.findByChannelIdAndUserIdAndDeleteYnIsFalse(CHANNEL_ID, LOGIN_ID))
            .thenReturn(Optional.of(membership(lastReadMessageId)));
    }

    private static Slice<ChatMessage> slice(int size, boolean hasNext, ChatMessage... messages) {
        return new SliceImpl<>(List.of(messages), PageRequest.of(0, size), hasNext);
    }

    private static Boolean hasNext(ResponseData<? extends Map<String, ?>> response) {
        return (Boolean) response.getData().get("hasNext");
    }

    private void givenChannelMember() {
        when(channelUserRepository.existsByChannelIdAndUserIdAndDeleteYnIsFalse(CHANNEL_ID, LOGIN_ID)).thenReturn(true);
    }

    private ChannelUser membership(String lastReadMessageId) {
        return ChannelUser.builder().channelId(CHANNEL_ID).userId(LOGIN_ID).lastReadMessageId(lastReadMessageId).deleteYn(false).build();
    }

    /**
     * 채널에 들어온 시각(createDt)은 저장할 때 정해지므로 테스트에서는 직접 넣는다
     */
    private ChannelUser membership(String lastReadMessageId, Instant joinedAt) {
        ChannelUser membership = membership(lastReadMessageId);
        ReflectionTestUtils.setField(membership, "createDt", joinedAt);
        return membership;
    }

    private ChatMessage message(String id, String content) {
        return ChatMessage.builder().id(new ObjectId(id)).channelId(CHANNEL_ID).content(content).createUserId(LOGIN_ID)
            .eventPublishDt(SENT_AT).build();
    }
}
