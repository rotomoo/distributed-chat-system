package com.distributed.chat.system.kafka.consumer.consumer;

import com.distributed.chat.system.kafka.consumer.base.kafka.ChatTopics;
import com.distributed.chat.system.kafka.consumer.service.ChatAttachmentResolver;
import com.distributed.chat.system.kafka.consumer.service.ChatMessageDeliveryService;
import com.distributed.chat.system.mongodb.collection.ChatMessage.Attachment;
import com.distributed.chat.system.mongodb.collection.ChatMessage.Reaction;
import com.distributed.chat.system.mongodb.repository.ChatMessageRepository;
import com.distributed.chat.system.mysql.repository.ChannelUserRepository;
import com.google.protobuf.InvalidProtocolBufferException;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.bson.types.ObjectId;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;
import protobuf.EdaChatMessage.ChatMessage;
import protobuf.EdaChatMessage.ChatReaction;

/**
 * 보낸 사람이 채널 멤버인지는 여기서 한 번만 확인한다 (원본 MySQL channel_user, 멤버 목록은 전달에도 그대로 쓴다).
 * 저장 / 전달 실패는 예외로 던진다 → 재시도 토픽으로 넘어가 나중에 다시 처리된다 (KafkaRetryTopicConfig).
 * 같은 레코드를 여러 번 처리해도 결과가 같다 : 메시지는 eventId 로 한 번만 저장, 리액션은 $addToSet / $pull
 */
@Component
@Slf4j
@RequiredArgsConstructor
public class EventConsumer {

    private final ChatMessageRepository chatMessageRepository;
    private final ChatMessageDeliveryService chatMessageDeliveryService;
    private final ChatAttachmentResolver chatAttachmentResolver;
    private final ChannelUserRepository channelUserRepository;

    @KafkaListener(topics = ChatTopics.CHAT_SEND_MESSAGE, groupId = "chat-group-1")
    public void consumeChatSendMessage(byte[] payload) {
        ChatMessage chatMessage;
        try {
            chatMessage = ChatMessage.parseFrom(payload);
        } catch (InvalidProtocolBufferException e) {
            // 재시도해도 파싱되지 않는 메시지이므로 버린다
            log.error("[chat-send-message] invalid payload : {}", e.getMessage());
            return;
        }
        String eventId = ChatTopics.eventIdOf(chatMessage);
        log.info("[chat-send-message] consumed: eventId={}, sendUserId={}, channelId={}",
            eventId, chatMessage.getSendUserId(), chatMessage.getChannelId());

        // 0. 채널 멤버 (원본 MySQL channel_user). 보낸 사람이 멤버가 아니면 버린다 (채팅 서버는 형식만 본다)
        List<Long> memberIds = channelUserRepository.findMemberUserIds(chatMessage.getChannelId());
        if (!memberIds.contains(chatMessage.getSendUserId())) {
            log.warn("[chat-send-message] not a channel member, dropped: sendUserId={}, channelId={}",
                chatMessage.getSendUserId(), chatMessage.getChannelId());
            return;
        }

        // 1. 첨부파일 확인 (GridFS metadata). 남의 파일 / 다른 채널 파일은 버린다
        List<Attachment> attachments = chatAttachmentResolver.resolve(chatMessage);
        if (chatMessage.getMessage().isBlank() && attachments.isEmpty()) {
            // 첨부만 보냈는데 전부 버려졌으면 남는 게 없다
            log.warn("[chat-send-message] empty message dropped: sendUserId={}, channelId={}",
                chatMessage.getSendUserId(), chatMessage.getChannelId());
            return;
        }

        // 2. 이력 저장 (eventId 로 한 번만. 재시도 / 중복 발행이면 처음 저장된 문서를 그대로 받는다)
        com.distributed.chat.system.mongodb.collection.ChatMessage saved = chatMessageRepository.saveOnce(
            com.distributed.chat.system.mongodb.collection.ChatMessage.builder()
                .eventId(eventId)
                .channelId(chatMessage.getChannelId())
                .content(chatMessage.getMessage())
                .eventPublishDt(Instant.ofEpochMilli(chatMessage.getPublishDt()))
                .createDt(Instant.now())
                .createUserId(chatMessage.getSendUserId())
                .attachments(attachments.isEmpty() ? null : attachments)
                .build());

        // 3. 멤버가 접속한 채팅 서버로 전달 (실패하면 예외 → 재시도 토픽)
        chatMessageDeliveryService.deliver(saved.getId().toHexString(), chatMessage, attachments, memberIds);
    }

    /**
     * 이모지 리액션 : 메시지 문서의 reactions 를 원자적으로 바꾸고, 바뀐 뒤의 reactions 전체를 채널 접속자에게 보낸다.
     * $addToSet / $pull 이라 같은 이벤트를 다시 처리해도 결과가 같아서 메시지처럼 eventId 가 필요 없다
     */
    @KafkaListener(topics = ChatTopics.CHAT_REACTION, groupId = "chat-group-1")
    public void consumeChatReaction(byte[] payload) {
        ChatReaction chatReaction;
        try {
            chatReaction = ChatReaction.parseFrom(payload);
        } catch (InvalidProtocolBufferException e) {
            log.error("[chat-reaction] invalid payload : {}", e.getMessage());
            return;
        }
        if (!ObjectId.isValid(chatReaction.getMessageId()) || chatReaction.getEmoji().isBlank()) {
            log.warn("[chat-reaction] invalid reaction dropped: messageId={}", chatReaction.getMessageId());
            return;
        }

        List<Long> memberIds = channelUserRepository.findMemberUserIds(chatReaction.getChannelId());
        if (!memberIds.contains(chatReaction.getSendUserId())) {
            log.warn("[chat-reaction] not a channel member, dropped: sendUserId={}, channelId={}",
                chatReaction.getSendUserId(), chatReaction.getChannelId());
            return;
        }

        ObjectId messageId = new ObjectId(chatReaction.getMessageId());
        Optional<List<Reaction>> reactions = switch (chatReaction.getAction()) {
            case ADD -> chatMessageRepository.addReaction(
                messageId, chatReaction.getChannelId(), chatReaction.getEmoji(), chatReaction.getSendUserId());
            case REMOVE -> chatMessageRepository.removeReaction(
                messageId, chatReaction.getChannelId(), chatReaction.getEmoji(), chatReaction.getSendUserId());
            default -> {
                log.warn("[chat-reaction] unknown action dropped: {}", chatReaction.getAction());
                yield Optional.empty();
            }
        };
        // 메시지가 없거나 다른 채널의 메시지면 버린다 (실패하면 예외 → 재시도 토픽)
        reactions.ifPresent(current -> chatMessageDeliveryService.deliverReaction(
            chatReaction.getChannelId(), messageId.toHexString(), current, memberIds));
    }
}
