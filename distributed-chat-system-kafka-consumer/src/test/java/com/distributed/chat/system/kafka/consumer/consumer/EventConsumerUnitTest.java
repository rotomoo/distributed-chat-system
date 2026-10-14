package com.distributed.chat.system.kafka.consumer.consumer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.distributed.chat.system.kafka.consumer.service.ChatAttachmentResolver;
import com.distributed.chat.system.kafka.consumer.service.DeliveryFailedException;
import com.distributed.chat.system.kafka.consumer.service.ChatMessageDeliveryService;
import com.distributed.chat.system.mongodb.collection.ChatMessage.Attachment;
import com.distributed.chat.system.mongodb.collection.ChatMessage.Reaction;
import com.distributed.chat.system.mongodb.repository.ChatMessageRepository;
import com.distributed.chat.system.mysql.repository.ChannelUserRepository;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.bson.types.ObjectId;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import protobuf.EdaChatMessage.ChatMessage;
import protobuf.EdaChatMessage.ChatReaction;

@DisplayName("[단위] EventConsumer 테스트")
@ExtendWith(MockitoExtension.class)
class EventConsumerUnitTest {

    private static final String MESSAGE_ID = "6acc66675f99715e286f0f6e";

    /**
     * 채널 멤버 (MySQL channel_user)
     */
    private static final List<Long> MEMBERS = List.of(5L, 9L);

    /**
     * 저장된 메시지 ID (전달할 때는 24자리 hex 문자열)
     */
    private static final String SAVED_ID = "6acc66675f99715e286f0f70";

    @Mock
    private ChatMessageRepository chatMessageRepository;

    @Mock
    private ChatMessageDeliveryService chatMessageDeliveryService;

    @Mock
    private ChatAttachmentResolver chatAttachmentResolver;

    @Mock
    private ChannelUserRepository channelUserRepository;

    @InjectMocks
    private EventConsumer eventConsumer;

    @BeforeEach
    void setup() {
        // 기본 : 보낸 사람(5)이 채널 멤버. 멤버가 아닌 경우는 따로 테스트
        lenient().when(channelUserRepository.findMemberUserIds(anyLong())).thenReturn(MEMBERS);
    }

    // ── 메시지 ──────────────────────────────────────────────────

    @Test
    @DisplayName("success case : 메시지 이벤트를 이력에 저장한 뒤 저장된 ID 로 전달한다")
    void 메시지_이벤트를_이력에_저장한_뒤_저장된_ID_로_전달한다() {
        // arrange
        ChatMessage chatMessage = ChatMessage.newBuilder()
            .setSendUserId(5L).setChannelId(0L).setMessage("hello").setPublishDt(1_700_000_000_000L).build();
        when(chatAttachmentResolver.resolve(chatMessage)).thenReturn(List.of());
        givenSavedAs(SAVED_ID);

        // act
        eventConsumer.consumeChatSendMessage(chatMessage.toByteArray());

        // assert
        com.distributed.chat.system.mongodb.collection.ChatMessage saved = savedMessage();
        assertThat(saved.getContent()).isEqualTo("hello");
        assertThat(saved.getCreateUserId()).isEqualTo(5L);
        assertThat(saved.getChannelId()).isEqualTo(0L);
        assertThat(saved.getEventPublishDt()).isEqualTo(Instant.ofEpochMilli(1_700_000_000_000L));
        assertThat(saved.getAttachments()).isNull();
        verify(chatMessageDeliveryService).deliver(eq(SAVED_ID), eq(chatMessage), eq(List.of()), eq(MEMBERS));
    }

    @Test
    @DisplayName("success case : 확인된 첨부만 저장하고 전달한다 (글 없이 첨부만 있어도 된다)")
    void 확인된_첨부만_저장하고_전달한다() {
        // arrange
        ChatMessage chatMessage = ChatMessage.newBuilder()
            .setSendUserId(5L).setChannelId(7L).setMessage("").addAttachmentFileIds(MESSAGE_ID).setPublishDt(1L).build();
        List<Attachment> attachments = List.of(new Attachment(MESSAGE_ID, "사진.png", "image/png", 10L));
        when(chatAttachmentResolver.resolve(chatMessage)).thenReturn(attachments);
        givenSavedAs(SAVED_ID);

        // act
        eventConsumer.consumeChatSendMessage(chatMessage.toByteArray());

        // assert
        assertThat(savedMessage().getAttachments()).isEqualTo(attachments);
        verify(chatMessageDeliveryService).deliver(SAVED_ID, chatMessage, attachments, MEMBERS);
    }

    @Test
    @DisplayName("fail case : 글이 없고 첨부가 전부 버려지면 저장하지도 전달하지도 않는다")
    void 글이_없고_첨부가_전부_버려지면_저장하지도_전달하지도_않는다() {
        // arrange
        ChatMessage chatMessage = ChatMessage.newBuilder()
            .setSendUserId(5L).setChannelId(7L).setMessage(" ").addAttachmentFileIds(MESSAGE_ID).setPublishDt(1L).build();
        when(chatAttachmentResolver.resolve(chatMessage)).thenReturn(List.of());

        // act
        eventConsumer.consumeChatSendMessage(chatMessage.toByteArray());

        // assert
        verify(chatMessageRepository, never()).saveOnce(any());
        verifyNoInteractions(chatMessageDeliveryService);
    }

    @Test
    @DisplayName("fail case : 프로토콜 버퍼로 읽을 수 없는 메시지는 저장하지도 전달하지도 않는다")
    void 프로토콜_버퍼로_읽을_수_없는_메시지는_저장하지도_전달하지도_않는다() {
        // arrange
        byte[] broken = {(byte) 0xFF, (byte) 0xFF, (byte) 0xFF};

        // act
        eventConsumer.consumeChatSendMessage(broken);

        // assert
        verify(chatMessageRepository, never()).saveOnce(any());
        verifyNoInteractions(chatMessageDeliveryService);
    }

    @Test
    @DisplayName("success case : 채팅 서버가 만든 eventId 로 한 번만 저장하고 저장된 ID 로 전달한다")
    void 채팅_서버가_만든_eventId_로_한_번만_저장하고_저장된_ID_로_전달한다() {
        // arrange
        ChatMessage chatMessage = message("hello").toBuilder().setEventId("event-1").build();
        when(chatAttachmentResolver.resolve(chatMessage)).thenReturn(List.of());
        givenSavedAs(SAVED_ID);

        // act
        eventConsumer.consumeChatSendMessage(chatMessage.toByteArray());

        // assert
        assertThat(savedMessage().getEventId()).isEqualTo("event-1");
        verify(chatMessageDeliveryService).deliver(SAVED_ID, chatMessage, List.of(), MEMBERS);
    }

    @Test
    @DisplayName("success case : eventId 가 없는 예전 이벤트는 본문으로 만든 같은 eventId 를 쓴다 (재시도해도 같은 값)")
    void eventId_가_없는_예전_이벤트는_본문으로_만든_같은_eventId_를_쓴다() {
        // arrange
        ChatMessage chatMessage = message("hello");
        when(chatAttachmentResolver.resolve(chatMessage)).thenReturn(List.of());
        givenSavedAs(SAVED_ID);

        // act
        eventConsumer.consumeChatSendMessage(chatMessage.toByteArray());
        eventConsumer.consumeChatSendMessage(chatMessage.toByteArray());

        // assert
        ArgumentCaptor<com.distributed.chat.system.mongodb.collection.ChatMessage> saved =
            ArgumentCaptor.forClass(com.distributed.chat.system.mongodb.collection.ChatMessage.class);
        verify(chatMessageRepository, times(2)).saveOnce(saved.capture());
        assertThat(saved.getAllValues().get(0).getEventId()).isNotBlank()
            .isEqualTo(saved.getAllValues().get(1).getEventId());
    }

    @Test
    @DisplayName("fail case : 저장이 실패하면 전달하지 않고 예외를 던진다 (재시도 토픽으로)")
    void 저장이_실패하면_전달하지_않고_예외를_던진다() {
        // arrange
        ChatMessage chatMessage = message("hello");
        when(chatAttachmentResolver.resolve(chatMessage)).thenReturn(List.of());
        when(chatMessageRepository.saveOnce(any())).thenThrow(new IllegalStateException("mongo down"));

        // act
        Throwable thrown = catchThrowable(() -> eventConsumer.consumeChatSendMessage(chatMessage.toByteArray()));

        // assert
        assertThat(thrown).isInstanceOf(IllegalStateException.class);
        verifyNoInteractions(chatMessageDeliveryService);
    }

    @Test
    @DisplayName("fail case : 전달이 실패하면 예외를 던진다 (저장은 끝났고 재시도 토픽이 다시 전달한다)")
    void 전달이_실패하면_예외를_던진다() {
        // arrange
        ChatMessage chatMessage = message("hello");
        when(chatAttachmentResolver.resolve(chatMessage)).thenReturn(List.of());
        givenSavedAs(SAVED_ID);
        doThrow(new DeliveryFailedException(List.of("10.0.0.1:1111")))
            .when(chatMessageDeliveryService).deliver(SAVED_ID, chatMessage, List.of(), MEMBERS);

        // act
        Throwable thrown = catchThrowable(() -> eventConsumer.consumeChatSendMessage(chatMessage.toByteArray()));

        // assert
        assertThat(thrown).isInstanceOf(DeliveryFailedException.class);
        verify(chatMessageRepository).saveOnce(any());
    }

    @Test
    @DisplayName("fail case : 보낸 사람이 채널 멤버가 아니면 저장하지도 전달하지도 않고 버린다")
    void 보낸_사람이_채널_멤버가_아니면_저장하지도_전달하지도_않고_버린다() {
        // arrange
        when(channelUserRepository.findMemberUserIds(7L)).thenReturn(List.of(9L, 17L));

        // act
        eventConsumer.consumeChatSendMessage(message("hello").toByteArray());

        // assert
        verify(chatMessageRepository, never()).saveOnce(any());
        verifyNoInteractions(chatMessageDeliveryService, chatAttachmentResolver);
    }

    // ── 리액션 ──────────────────────────────────────────────────

    @Test
    @DisplayName("fail case : 리액션을 누른 사람이 채널 멤버가 아니면 반영하지도 전달하지도 않는다")
    void 리액션을_누른_사람이_채널_멤버가_아니면_반영하지도_전달하지도_않는다() {
        // arrange
        when(channelUserRepository.findMemberUserIds(7L)).thenReturn(List.of(9L));

        // act
        eventConsumer.consumeChatReaction(reaction(ChatReaction.Action.ADD).toByteArray());

        // assert
        verifyNoInteractions(chatMessageRepository, chatMessageDeliveryService);
    }

    @Test
    @DisplayName("success case : add 는 리액션을 더하고 바뀐 뒤의 reactions 전체를 채널에 전달한다")
    void add_는_리액션을_더하고_바뀐_뒤의_reactions_전체를_채널에_전달한다() {
        // arrange
        List<Reaction> after = List.of(new Reaction("👍", List.of(1L, 5L)));
        when(chatMessageRepository.addReaction(new ObjectId(MESSAGE_ID), 7L, "👍", 5L)).thenReturn(Optional.of(after));

        // act
        eventConsumer.consumeChatReaction(reaction(ChatReaction.Action.ADD).toByteArray());

        // assert
        verify(chatMessageDeliveryService).deliverReaction(7L, MESSAGE_ID, after, MEMBERS);
        verify(chatMessageRepository, never()).removeReaction(any(), any(), any(), any());
    }

    @Test
    @DisplayName("success case : remove 는 리액션을 빼고 바뀐 뒤의 reactions 전체를 채널에 전달한다")
    void remove_는_리액션을_빼고_바뀐_뒤의_reactions_전체를_채널에_전달한다() {
        // arrange
        when(chatMessageRepository.removeReaction(new ObjectId(MESSAGE_ID), 7L, "👍", 5L)).thenReturn(Optional.of(List.of()));

        // act
        eventConsumer.consumeChatReaction(reaction(ChatReaction.Action.REMOVE).toByteArray());

        // assert
        verify(chatMessageDeliveryService).deliverReaction(7L, MESSAGE_ID, List.of(), MEMBERS);
        verify(chatMessageRepository, never()).addReaction(any(), any(), any(), any());
    }

    @Test
    @DisplayName("fail case : 메시지가 없거나 다른 채널 메시지면 전달하지 않는다")
    void 메시지가_없거나_다른_채널_메시지면_전달하지_않는다() {
        // arrange
        when(chatMessageRepository.addReaction(new ObjectId(MESSAGE_ID), 7L, "👍", 5L)).thenReturn(Optional.empty());

        // act
        eventConsumer.consumeChatReaction(reaction(ChatReaction.Action.ADD).toByteArray());

        // assert
        verifyNoInteractions(chatMessageDeliveryService);
    }

    @Test
    @DisplayName("fail case : action 이 없거나 메시지 ID / 이모지가 틀리거나 읽을 수 없으면 아무것도 바꾸지 않는다")
    void action_이_없거나_메시지_ID_이모지가_틀리거나_읽을_수_없으면_아무것도_바꾸지_않는다() {
        // act
        eventConsumer.consumeChatReaction(reaction(ChatReaction.Action.ACTION_UNSPECIFIED).toByteArray());
        eventConsumer.consumeChatReaction(reaction(ChatReaction.Action.ADD).toBuilder().setMessageId("abc").build().toByteArray());
        eventConsumer.consumeChatReaction(reaction(ChatReaction.Action.ADD).toBuilder().setEmoji(" ").build().toByteArray());
        eventConsumer.consumeChatReaction(new byte[]{(byte) 0xFF, (byte) 0xFF});

        // assert
        verifyNoInteractions(chatMessageRepository, chatMessageDeliveryService);
    }

    @Test
    @DisplayName("fail case : 리액션 전달이 실패하면 예외를 던진다 (갱신은 $addToSet 이라 다시 해도 같다)")
    void 리액션_전달이_실패하면_예외를_던진다() {
        // arrange
        List<Reaction> after = List.of(new Reaction("👍", List.of(5L)));
        when(chatMessageRepository.addReaction(new ObjectId(MESSAGE_ID), 7L, "👍", 5L)).thenReturn(Optional.of(after));
        doThrow(new DeliveryFailedException(List.of("10.0.0.1:1111"))).when(chatMessageDeliveryService).deliverReaction(7L, MESSAGE_ID, after, MEMBERS);

        // act
        Throwable thrown = catchThrowable(() -> eventConsumer.consumeChatReaction(reaction(ChatReaction.Action.ADD).toByteArray()));

        // assert
        assertThat(thrown).isInstanceOf(DeliveryFailedException.class);
    }

    private ChatMessage message(String text) {
        return ChatMessage.newBuilder().setSendUserId(5L).setChannelId(7L).setMessage(text).setPublishDt(1_700_000_000_000L).build();
    }

    private ChatReaction reaction(ChatReaction.Action action) {
        return ChatReaction.newBuilder()
            .setSendUserId(5L).setChannelId(7L).setMessageId(MESSAGE_ID).setEmoji("👍").setAction(action)
            .setPublishDt(1_700_000_000_000L).build();
    }

    private void givenSavedAs(String id) {
        when(chatMessageRepository.saveOnce(any())).thenAnswer(invocation ->
            com.distributed.chat.system.mongodb.collection.ChatMessage.builder().id(new ObjectId(id)).build());
    }

    private com.distributed.chat.system.mongodb.collection.ChatMessage savedMessage() {
        ArgumentCaptor<com.distributed.chat.system.mongodb.collection.ChatMessage> saved =
            ArgumentCaptor.forClass(com.distributed.chat.system.mongodb.collection.ChatMessage.class);
        verify(chatMessageRepository).saveOnce(saved.capture());
        return saved.getValue();
    }
}
