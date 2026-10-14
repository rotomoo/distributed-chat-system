package com.distributed.chat.system.kafka.consumer.consumer;

import com.distributed.chat.system.common.event.ChatMessageEvent;
import com.distributed.chat.system.common.event.ChatReactionEvent;
import com.distributed.chat.system.common.event.EventJson;
import com.distributed.chat.system.kafka.consumer.base.kafka.ChatTopics;
import com.distributed.chat.system.kafka.consumer.service.ChatAttachmentResolver;
import com.distributed.chat.system.kafka.consumer.service.ChatMessageDeliveryService;
import com.distributed.chat.system.kafka.consumer.service.ChatNotificationPublisher;
import com.distributed.chat.system.kafka.consumer.service.ChatReceivers;
import com.distributed.chat.system.mongodb.collection.ChatMessage;
import com.distributed.chat.system.mongodb.collection.ChatMessage.Attachment;
import com.distributed.chat.system.mongodb.collection.ChatMessage.Reaction;
import com.distributed.chat.system.mongodb.repository.ChatMessageRepository;
import com.distributed.chat.system.mysql.repository.ChannelUserRepository;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.bson.types.ObjectId;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

/**
 * 보낸 사람이 채널 멤버인지는 여기서 한 번만 확인한다 (원본 MySQL channel_user, 멤버 목록은 전달에도 그대로 쓴다).
 * 저장 / 전달 실패는 예외로 던진다 → 재시도 토픽으로 넘어가 나중에 다시 처리된다 (KafkaRetryTopicConfig).
 * 같은 레코드를 여러 번 처리해도 결과가 같다 : 메시지는 eventId 로 한 번만 저장, 리액션은 $addToSet / $pull.
 * 오프라인 멤버 알림(chat-notification)은 메시지가 처음 저장될 때 한 번만 발행한다 (재시도 / 중복 발행 / DLT 재발행으로 알림이 다시 가지 않게)
 * <p>
 * 값은 이벤트 JSON 문자열 ({@link EventJson}). 읽을 수 없으면 {@link com.distributed.chat.system.common.event.MalformedEventException}
 * → 재시도하지 않고 바로 DLT (KafkaRetryTopicConfig, 다시 읽어도 같다). 읽히지만 값이 틀린 이벤트(멤버 아님, 모르는 action 등)는 로그만 남기고 버린다
 */
@Component
@Slf4j
@RequiredArgsConstructor
public class EventConsumer {

    private final ChatMessageRepository chatMessageRepository;
    private final ChatMessageDeliveryService chatMessageDeliveryService;
    private final ChatAttachmentResolver chatAttachmentResolver;
    private final ChannelUserRepository channelUserRepository;
    private final ChatNotificationPublisher chatNotificationPublisher;

    @KafkaListener(topics = ChatTopics.CHAT_SEND_MESSAGE, groupId = "chat-group-1")
    public void consumeChatSendMessage(String payload) {
        ChatMessageEvent chatMessage = EventJson.read(payload, ChatMessageEvent.class);
        String eventId = ChatTopics.eventIdOf(chatMessage);
        log.info("[chat-send-message] consumed: eventId={}, sendUserId={}, channelId={}",
            eventId, chatMessage.sendUserId(), chatMessage.channelId());

        // 0. 채널 멤버 (원본 MySQL channel_user). 보낸 사람이 멤버가 아니면 버린다 (채팅 서버는 형식만 본다)
        List<Long> memberIds = channelUserRepository.findMemberUserIds(chatMessage.channelId());
        if (!memberIds.contains(chatMessage.sendUserId())) {
            log.warn("[chat-send-message] not a channel member, dropped: sendUserId={}, channelId={}",
                chatMessage.sendUserId(), chatMessage.channelId());
            return;
        }

        // 1. 첨부파일 확인 (GridFS metadata). 남의 파일 / 다른 채널 파일은 버린다
        List<Attachment> attachments = chatAttachmentResolver.resolve(chatMessage);
        if (chatMessage.message().isBlank() && attachments.isEmpty()) {
            // 첨부만 보냈는데 전부 버려졌으면 남는 게 없다
            log.warn("[chat-send-message] empty message dropped: sendUserId={}, channelId={}",
                chatMessage.sendUserId(), chatMessage.channelId());
            return;
        }

        // 2. 이력 저장 (eventId 로 한 번만. 재시도 / 중복 발행이면 처음 저장된 문서를 그대로 받는다)
        //    MongoDB Date 는 밀리초까지라 미리 잘라 두면 돌아온 createDt 가 같을 때 = 이번에 처음 저장한 것
        Instant createDt = Instant.now().truncatedTo(ChronoUnit.MILLIS);
        ChatMessage saved = chatMessageRepository.saveOnce(
            ChatMessage.builder()
                .eventId(eventId)
                .channelId(chatMessage.channelId())
                .content(chatMessage.message())
                .eventPublishDt(Instant.ofEpochMilli(chatMessage.publishDt()))
                .createDt(createDt)
                .createUserId(chatMessage.sendUserId())
                .attachments(attachments.isEmpty() ? null : attachments)
                .build());

        String messageId = saved.getId().toHexString();

        // 3. 접속 위치 (Redis MGET) → 접속한 멤버 / 오프라인 멤버
        ChatReceivers receivers = chatMessageDeliveryService.locate(memberIds);

        // 4. 오프라인 멤버 알림 (처음 저장했을 때만). 전달보다 먼저 : 전달이 실패해 재시도로 넘어가도 알림은 이미 나갔다.
        //    발행 실패는 레코드를 실패시키지 않는다 (ChatNotificationPublisher)
        if (createDt.equals(saved.getCreateDt())) {
            chatNotificationPublisher.publish(messageId, chatMessage, attachments.size(), receivers.offline());
        }

        // 5. 접속한 멤버의 채팅 서버로 전달 (실패하면 예외 → 재시도 토픽)
        chatMessageDeliveryService.deliver(messageId, chatMessage, attachments, receivers);
    }

    /**
     * 이모지 리액션 : 메시지 문서의 reactions 를 원자적으로 바꾸고, 바뀐 뒤의 reactions 전체를 채널 접속자에게 보낸다 (알림 없음).
     * $addToSet / $pull 이라 같은 이벤트를 다시 처리해도 결과가 같아서 메시지처럼 eventId 가 필요 없다
     */
    @KafkaListener(topics = ChatTopics.CHAT_REACTION, groupId = "chat-group-1")
    public void consumeChatReaction(String payload) {
        ChatReactionEvent chatReaction = EventJson.read(payload, ChatReactionEvent.class);
        if (!ObjectId.isValid(chatReaction.messageId()) || chatReaction.emoji().isBlank()) {
            log.warn("[chat-reaction] invalid reaction dropped: messageId={}", chatReaction.messageId());
            return;
        }
        if (chatReaction.action() == null) {
            // 없거나 이 컨슈머가 모르는 action (새 채팅 서버가 더한 값 등)
            log.warn("[chat-reaction] unknown action dropped: messageId={}", chatReaction.messageId());
            return;
        }

        List<Long> memberIds = channelUserRepository.findMemberUserIds(chatReaction.channelId());
        if (!memberIds.contains(chatReaction.sendUserId())) {
            log.warn("[chat-reaction] not a channel member, dropped: sendUserId={}, channelId={}",
                chatReaction.sendUserId(), chatReaction.channelId());
            return;
        }

        ObjectId messageId = new ObjectId(chatReaction.messageId());
        Optional<List<Reaction>> reactions = switch (chatReaction.action()) {
            case ADD -> chatMessageRepository.addReaction(
                messageId, chatReaction.channelId(), chatReaction.emoji(), chatReaction.sendUserId());
            case REMOVE -> chatMessageRepository.removeReaction(
                messageId, chatReaction.channelId(), chatReaction.emoji(), chatReaction.sendUserId());
        };
        // 메시지가 없거나 다른 채널의 메시지면 버린다 (실패하면 예외 → 재시도 토픽)
        reactions.ifPresent(current -> chatMessageDeliveryService.deliverReaction(
            chatReaction.channelId(), messageId.toHexString(), current, memberIds));
    }
}
