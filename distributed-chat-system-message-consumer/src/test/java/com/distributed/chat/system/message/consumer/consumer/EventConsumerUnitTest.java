package com.distributed.chat.system.message.consumer.consumer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.distributed.chat.system.common.event.ChatMessageEvent;
import com.distributed.chat.system.common.event.ChatReactionEvent;
import com.distributed.chat.system.common.event.ChatReactionEvent.Action;
import com.distributed.chat.system.common.event.EventJson;
import com.distributed.chat.system.common.event.MalformedEventException;
import com.distributed.chat.system.message.consumer.service.ChatAttachmentResolver;
import com.distributed.chat.system.message.consumer.service.DeliveryFailedException;
import com.distributed.chat.system.message.consumer.service.ChatMessageDeliveryService;
import com.distributed.chat.system.message.consumer.service.ChatNotificationPublisher;
import com.distributed.chat.system.message.consumer.service.ChatReceivers;
import com.distributed.chat.system.mongodb.collection.ChatMessage;
import com.distributed.chat.system.mongodb.collection.ChatMessage.Attachment;
import com.distributed.chat.system.mongodb.collection.ChatMessage.Reaction;
import com.distributed.chat.system.mongodb.repository.ChatMessageRepository;
import com.distributed.chat.system.mysql.repository.ChannelUserRepository;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.bson.types.ObjectId;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@DisplayName("[단위] EventConsumer 테스트")
@ExtendWith(MockitoExtension.class)
class EventConsumerUnitTest {

    private static final String MESSAGE_ID = "6acc66675f99715e286f0f6e";

    /**
     * 채널 멤버 (MySQL channel_user)
     */
    private static final List<Long> MEMBERS = List.of(5L, 9L);

    /**
     * 접속 위치로 나눈 결과 : 5 는 접속 중, 9 는 오프라인
     */
    private static final ChatReceivers RECEIVERS = new ChatReceivers(Map.of("10.0.0.1:1111", List.of(5L)), List.of(9L));

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

    @Mock
    private ChatNotificationPublisher chatNotificationPublisher;

    @InjectMocks
    private EventConsumer eventConsumer;

    @BeforeEach
    void setup() {
        // 기본 : 보낸 사람(5)이 채널 멤버. 멤버가 아닌 경우는 따로 테스트
        lenient().when(channelUserRepository.findMemberUserIds(anyLong())).thenReturn(MEMBERS);
        lenient().when(chatMessageDeliveryService.locate(MEMBERS)).thenReturn(RECEIVERS);
    }

    // ── 메시지 ──────────────────────────────────────────────────

    @Test
    @DisplayName("success case : 메시지 이벤트를 이력에 저장한 뒤 저장된 ID 로 전달한다")
    void 메시지_이벤트를_이력에_저장한_뒤_저장된_ID_로_전달한다() {
        // arrange
        ChatMessageEvent chatMessage = new ChatMessageEvent("event-1", 5L, 0L, "hello", 1_700_000_000_000L, List.of());
        when(chatAttachmentResolver.resolve(chatMessage)).thenReturn(List.of());
        givenSavedAs(SAVED_ID);

        // act
        eventConsumer.consumeChatSendMessage(EventJson.write(chatMessage));

        // assert
        ChatMessage saved = savedMessage();
        assertThat(saved.getContent()).isEqualTo("hello");
        assertThat(saved.getCreateUserId()).isEqualTo(5L);
        assertThat(saved.getChannelId()).isEqualTo(0L);
        assertThat(saved.getEventPublishDt()).isEqualTo(Instant.ofEpochMilli(1_700_000_000_000L));
        assertThat(saved.getAttachments()).isNull();
        verify(chatMessageDeliveryService).deliver(eq(SAVED_ID), eq(chatMessage), eq(List.of()), eq(RECEIVERS));
    }

    @Test
    @DisplayName("success case : 확인된 첨부만 저장하고 전달한다 (글 없이 첨부만 있어도 된다)")
    void 확인된_첨부만_저장하고_전달한다() {
        // arrange
        ChatMessageEvent chatMessage = new ChatMessageEvent("event-1", 5L, 7L, "", 1L, List.of(MESSAGE_ID));
        List<Attachment> attachments = List.of(new Attachment(MESSAGE_ID, "사진.png", "image/png", 10L));
        when(chatAttachmentResolver.resolve(chatMessage)).thenReturn(attachments);
        givenSavedAs(SAVED_ID);

        // act
        eventConsumer.consumeChatSendMessage(EventJson.write(chatMessage));

        // assert
        assertThat(savedMessage().getAttachments()).isEqualTo(attachments);
        verify(chatMessageDeliveryService).deliver(SAVED_ID, chatMessage, attachments, RECEIVERS);
        verify(chatNotificationPublisher).publish(SAVED_ID, chatMessage, 1, List.of(9L));
    }

    @Test
    @DisplayName("fail case : 글이 없고 첨부가 전부 버려지면 저장하지도 전달하지도 않는다")
    void 글이_없고_첨부가_전부_버려지면_저장하지도_전달하지도_않는다() {
        // arrange
        ChatMessageEvent chatMessage = new ChatMessageEvent("event-1", 5L, 7L, " ", 1L, List.of(MESSAGE_ID));
        when(chatAttachmentResolver.resolve(chatMessage)).thenReturn(List.of());

        // act
        eventConsumer.consumeChatSendMessage(EventJson.write(chatMessage));

        // assert
        verify(chatMessageRepository, never()).saveOnce(any());
        verifyNoInteractions(chatMessageDeliveryService, chatNotificationPublisher);
    }

    @Test
    @DisplayName("fail case : 이벤트로 읽을 수 없는 메시지(깨진 JSON, 예전 바이너리 값)는 아무것도 하지 않고 MalformedEventException 을 던진다 (재시도 없이 DLT 로)")
    void 이벤트로_읽을_수_없는_메시지는_아무것도_하지_않고_MalformedEventException_을_던진다() {
        // arrange
        String binary = new String(new byte[]{0x08, 0x05, 0x10, 0x07, 0x1a, 0x02, 'h', 'i'}, java.nio.charset.StandardCharsets.UTF_8);

        // act
        Throwable brokenJson = catchThrowable(() -> eventConsumer.consumeChatSendMessage("{\"channelId\":"));
        Throwable oldBinary = catchThrowable(() -> eventConsumer.consumeChatSendMessage(binary));

        // assert
        assertThat(brokenJson).isInstanceOf(MalformedEventException.class);
        assertThat(oldBinary).isInstanceOf(MalformedEventException.class);
        verifyNoInteractions(channelUserRepository, chatMessageRepository, chatMessageDeliveryService, chatNotificationPublisher);
    }

    @Test
    @DisplayName("success case : 모르는 필드(새 채팅 서버가 더한 필드)가 있어도 아는 필드로 그대로 저장하고 전달한다")
    void 모르는_필드가_있어도_아는_필드로_그대로_저장하고_전달한다() {
        // arrange
        String payload = "{\"eventId\":\"event-1\",\"sendUserId\":5,\"channelId\":7,\"message\":\"hello\",\"publishDt\":1700000000000,"
            + "\"threadId\":\"t-1\",\"mentions\":[{\"userId\":9}]}";
        ChatMessageEvent expected = message("hello");
        when(chatAttachmentResolver.resolve(expected)).thenReturn(List.of());
        givenSavedAs(SAVED_ID);

        // act
        eventConsumer.consumeChatSendMessage(payload);

        // assert
        assertThat(savedMessage().getEventId()).isEqualTo("event-1");
        verify(chatMessageDeliveryService).deliver(SAVED_ID, expected, List.of(), RECEIVERS);
    }

    @Test
    @DisplayName("success case : 채팅 서버가 만든 eventId 로 한 번만 저장하고 저장된 ID 로 전달한다")
    void 채팅_서버가_만든_eventId_로_한_번만_저장하고_저장된_ID_로_전달한다() {
        // arrange
        ChatMessageEvent chatMessage = message("hello");
        when(chatAttachmentResolver.resolve(chatMessage)).thenReturn(List.of());
        givenSavedAs(SAVED_ID);

        // act
        eventConsumer.consumeChatSendMessage(EventJson.write(chatMessage));

        // assert
        assertThat(savedMessage().getEventId()).isEqualTo("event-1");
        verify(chatMessageDeliveryService).deliver(SAVED_ID, chatMessage, List.of(), RECEIVERS);
    }

    @Test
    @DisplayName("success case : eventId 가 없는 이벤트는 이벤트 JSON 으로 만든 같은 eventId 를 쓰고 (재시도해도 같은 값), 다른 이벤트와 합쳐지지 않는다")
    void eventId_가_없는_이벤트는_이벤트_JSON_으로_만든_같은_eventId_를_쓴다() {
        // arrange
        ChatMessageEvent withoutEventId = new ChatMessageEvent(null, 5L, 7L, "hello", 1_700_000_000_000L, List.of());
        ChatMessageEvent another = new ChatMessageEvent(null, 5L, 7L, "bye", 1_700_000_000_000L, List.of());
        when(chatAttachmentResolver.resolve(any())).thenReturn(List.of());
        givenSavedAs(SAVED_ID);

        // act
        eventConsumer.consumeChatSendMessage(EventJson.write(withoutEventId));
        eventConsumer.consumeChatSendMessage(EventJson.write(withoutEventId));
        eventConsumer.consumeChatSendMessage(EventJson.write(another));

        // assert
        ArgumentCaptor<ChatMessage> saved = ArgumentCaptor.forClass(ChatMessage.class);
        verify(chatMessageRepository, times(3)).saveOnce(saved.capture());
        assertThat(saved.getAllValues().get(0).getEventId()).isNotBlank()
            .isEqualTo(saved.getAllValues().get(1).getEventId())
            .isNotEqualTo(saved.getAllValues().get(2).getEventId());
    }

    @Test
    @DisplayName("fail case : 저장이 실패하면 전달하지 않고 예외를 던진다 (재시도 토픽으로)")
    void 저장이_실패하면_전달하지_않고_예외를_던진다() {
        // arrange
        ChatMessageEvent chatMessage = message("hello");
        when(chatAttachmentResolver.resolve(chatMessage)).thenReturn(List.of());
        when(chatMessageRepository.saveOnce(any())).thenThrow(new IllegalStateException("mongo down"));

        // act
        Throwable thrown = catchThrowable(() -> eventConsumer.consumeChatSendMessage(EventJson.write(chatMessage)));

        // assert
        assertThat(thrown).isInstanceOf(IllegalStateException.class);
        verifyNoInteractions(chatMessageDeliveryService, chatNotificationPublisher);
    }

    @Test
    @DisplayName("fail case : 전달이 실패하면 예외를 던진다 (저장은 끝났고 재시도 토픽이 다시 전달한다)")
    void 전달이_실패하면_예외를_던진다() {
        // arrange
        ChatMessageEvent chatMessage = message("hello");
        when(chatAttachmentResolver.resolve(chatMessage)).thenReturn(List.of());
        givenSavedAs(SAVED_ID);
        doThrow(new DeliveryFailedException(List.of("10.0.0.1:1111")))
            .when(chatMessageDeliveryService).deliver(SAVED_ID, chatMessage, List.of(), RECEIVERS);

        // act
        Throwable thrown = catchThrowable(() -> eventConsumer.consumeChatSendMessage(EventJson.write(chatMessage)));

        // assert : 알림은 전달 전에 이미 발행됐다 (재시도에서는 다시 발행하지 않으므로 여기서 놓치면 안 된다)
        assertThat(thrown).isInstanceOf(DeliveryFailedException.class);
        verify(chatMessageRepository).saveOnce(any());
        verify(chatNotificationPublisher).publish(SAVED_ID, chatMessage, 0, List.of(9L));
    }

    // ── 알림 (chat-notification) ─────────────────────────────────

    @Test
    @DisplayName("success case : 처음 저장한 메시지는 오프라인 멤버 알림을 전달보다 먼저 발행한다")
    void 처음_저장한_메시지는_오프라인_멤버_알림을_전달보다_먼저_발행한다() {
        // arrange
        ChatMessageEvent chatMessage = message("점심 뭐 먹을까요?");
        when(chatAttachmentResolver.resolve(chatMessage)).thenReturn(List.of());
        givenSavedAs(SAVED_ID);

        // act
        eventConsumer.consumeChatSendMessage(EventJson.write(chatMessage));

        // assert
        InOrder order = inOrder(chatNotificationPublisher, chatMessageDeliveryService);
        order.verify(chatNotificationPublisher).publish(SAVED_ID, chatMessage, 0, List.of(9L));
        order.verify(chatMessageDeliveryService).deliver(SAVED_ID, chatMessage, List.of(), RECEIVERS);
    }

    @Test
    @DisplayName("success case : 이미 저장돼 있던 메시지(재시도 / 중복 발행 / DLT 재발행)는 전달만 다시 하고 알림은 다시 발행하지 않는다")
    void 이미_저장돼_있던_메시지는_전달만_다시_하고_알림은_다시_발행하지_않는다() {
        // arrange
        ChatMessageEvent chatMessage = message("hello");
        when(chatAttachmentResolver.resolve(chatMessage)).thenReturn(List.of());
        givenAlreadySavedAs(SAVED_ID);

        // act
        eventConsumer.consumeChatSendMessage(EventJson.write(chatMessage));

        // assert
        verify(chatMessageDeliveryService).deliver(SAVED_ID, chatMessage, List.of(), RECEIVERS);
        verifyNoInteractions(chatNotificationPublisher);
    }

    @Test
    @DisplayName("fail case : 접속 위치 조회가 실패하면 알림도 전달도 하지 않고 예외를 던진다 (재시도 토픽으로)")
    void 접속_위치_조회가_실패하면_알림도_전달도_하지_않고_예외를_던진다() {
        // arrange
        ChatMessageEvent chatMessage = message("hello");
        when(chatAttachmentResolver.resolve(chatMessage)).thenReturn(List.of());
        givenSavedAs(SAVED_ID);
        when(chatMessageDeliveryService.locate(MEMBERS)).thenThrow(new IllegalStateException("redis down"));

        // act
        Throwable thrown = catchThrowable(() -> eventConsumer.consumeChatSendMessage(EventJson.write(chatMessage)));

        // assert
        assertThat(thrown).isInstanceOf(IllegalStateException.class);
        verifyNoInteractions(chatNotificationPublisher);
        verify(chatMessageDeliveryService, never()).deliver(any(), any(), any(), any());
    }

    @Test
    @DisplayName("fail case : 보낸 사람이 채널 멤버가 아니면 저장하지도 전달하지도 않고 버린다")
    void 보낸_사람이_채널_멤버가_아니면_저장하지도_전달하지도_않고_버린다() {
        // arrange
        when(channelUserRepository.findMemberUserIds(7L)).thenReturn(List.of(9L, 17L));

        // act
        eventConsumer.consumeChatSendMessage(EventJson.write(message("hello")));

        // assert
        verify(chatMessageRepository, never()).saveOnce(any());
        verifyNoInteractions(chatMessageDeliveryService, chatAttachmentResolver, chatNotificationPublisher);
    }

    // ── 리액션 ──────────────────────────────────────────────────

    @Test
    @DisplayName("fail case : 리액션을 누른 사람이 채널 멤버가 아니면 반영하지도 전달하지도 않는다")
    void 리액션을_누른_사람이_채널_멤버가_아니면_반영하지도_전달하지도_않는다() {
        // arrange
        when(channelUserRepository.findMemberUserIds(7L)).thenReturn(List.of(9L));

        // act
        eventConsumer.consumeChatReaction(EventJson.write(reaction(Action.ADD)));

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
        eventConsumer.consumeChatReaction(EventJson.write(reaction(Action.ADD)));

        // assert
        verify(chatMessageDeliveryService).deliverReaction(7L, MESSAGE_ID, after, MEMBERS);
        verify(chatMessageRepository, never()).removeReaction(any(), any(), any(), any());
        // 리액션은 알림을 만들지 않는다
        verifyNoInteractions(chatNotificationPublisher);
    }

    @Test
    @DisplayName("success case : remove 는 리액션을 빼고 바뀐 뒤의 reactions 전체를 채널에 전달한다")
    void remove_는_리액션을_빼고_바뀐_뒤의_reactions_전체를_채널에_전달한다() {
        // arrange
        when(chatMessageRepository.removeReaction(new ObjectId(MESSAGE_ID), 7L, "👍", 5L)).thenReturn(Optional.of(List.of()));

        // act
        eventConsumer.consumeChatReaction(EventJson.write(reaction(Action.REMOVE)));

        // assert
        verify(chatMessageDeliveryService).deliverReaction(7L, MESSAGE_ID, List.of(), MEMBERS);
        verify(chatMessageRepository, never()).addReaction(any(), any(), any(), any());
        verifyNoInteractions(chatNotificationPublisher);
    }

    @Test
    @DisplayName("fail case : 메시지가 없거나 다른 채널 메시지면 전달하지 않는다")
    void 메시지가_없거나_다른_채널_메시지면_전달하지_않는다() {
        // arrange
        when(chatMessageRepository.addReaction(new ObjectId(MESSAGE_ID), 7L, "👍", 5L)).thenReturn(Optional.empty());

        // act
        eventConsumer.consumeChatReaction(EventJson.write(reaction(Action.ADD)));

        // assert
        verifyNoInteractions(chatMessageDeliveryService);
    }

    @Test
    @DisplayName("fail case : action 이 없거나 모르는 값이거나 메시지 ID / 이모지가 틀리면 아무것도 바꾸지 않고 버린다 (예외 없음)")
    void action_이_없거나_모르는_값이거나_메시지_ID_이모지가_틀리면_아무것도_바꾸지_않고_버린다() {
        // act
        eventConsumer.consumeChatReaction(EventJson.write(reaction(null)));
        eventConsumer.consumeChatReaction("{\"sendUserId\":5,\"channelId\":7,\"messageId\":\"" + MESSAGE_ID + "\",\"emoji\":\"👍\",\"action\":\"TOGGLE\"}");
        eventConsumer.consumeChatReaction(EventJson.write(new ChatReactionEvent(5L, 7L, "abc", "👍", Action.ADD, 1L)));
        eventConsumer.consumeChatReaction(EventJson.write(new ChatReactionEvent(5L, 7L, MESSAGE_ID, " ", Action.ADD, 1L)));

        // assert
        verifyNoInteractions(chatMessageRepository, chatMessageDeliveryService);
    }

    @Test
    @DisplayName("fail case : 이벤트로 읽을 수 없는 리액션은 아무것도 하지 않고 MalformedEventException 을 던진다 (재시도 없이 DLT 로)")
    void 이벤트로_읽을_수_없는_리액션은_아무것도_하지_않고_MalformedEventException_을_던진다() {
        // act
        Throwable thrown = catchThrowable(() -> eventConsumer.consumeChatReaction("not json"));

        // assert
        assertThat(thrown).isInstanceOf(MalformedEventException.class);
        verifyNoInteractions(channelUserRepository, chatMessageRepository, chatMessageDeliveryService);
    }

    @Test
    @DisplayName("fail case : 리액션 전달이 실패하면 예외를 던진다 (갱신은 $addToSet 이라 다시 해도 같다)")
    void 리액션_전달이_실패하면_예외를_던진다() {
        // arrange
        List<Reaction> after = List.of(new Reaction("👍", List.of(5L)));
        when(chatMessageRepository.addReaction(new ObjectId(MESSAGE_ID), 7L, "👍", 5L)).thenReturn(Optional.of(after));
        doThrow(new DeliveryFailedException(List.of("10.0.0.1:1111"))).when(chatMessageDeliveryService).deliverReaction(7L, MESSAGE_ID, after, MEMBERS);

        // act
        Throwable thrown = catchThrowable(() -> eventConsumer.consumeChatReaction(EventJson.write(reaction(Action.ADD))));

        // assert
        assertThat(thrown).isInstanceOf(DeliveryFailedException.class);
    }

    private ChatMessageEvent message(String text) {
        return new ChatMessageEvent("event-1", 5L, 7L, text, 1_700_000_000_000L, List.of());
    }

    private ChatReactionEvent reaction(Action action) {
        return new ChatReactionEvent(5L, 7L, MESSAGE_ID, "👍", action, 1_700_000_000_000L);
    }

    /**
     * 처음 저장 : MongoDB 가 넘긴 createDt 그대로 넣은 문서를 돌려준다
     */
    private void givenSavedAs(String id) {
        when(chatMessageRepository.saveOnce(any())).thenAnswer(invocation ->
            ChatMessage.builder().id(new ObjectId(id))
                .createDt(invocation.<ChatMessage>getArgument(0).getCreateDt()).build());
    }

    /**
     * 이미 저장돼 있음 : 예전에 저장된 문서(다른 createDt)를 그대로 돌려준다
     */
    private void givenAlreadySavedAs(String id) {
        when(chatMessageRepository.saveOnce(any())).thenAnswer(invocation ->
            ChatMessage.builder().id(new ObjectId(id))
                .createDt(Instant.ofEpochMilli(1_600_000_000_000L)).build());
    }

    private ChatMessage savedMessage() {
        ArgumentCaptor<ChatMessage> saved =
            ArgumentCaptor.forClass(ChatMessage.class);
        verify(chatMessageRepository).saveOnce(saved.capture());
        return saved.getValue();
    }
}
