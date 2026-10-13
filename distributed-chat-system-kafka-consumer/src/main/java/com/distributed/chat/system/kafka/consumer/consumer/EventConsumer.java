package com.distributed.chat.system.kafka.consumer.consumer;

import com.distributed.chat.system.kafka.consumer.base.kafka.ChatTopics;
import com.distributed.chat.system.kafka.consumer.base.metrics.ConsumerMetrics;
import com.distributed.chat.system.kafka.consumer.service.ChatAttachmentResolver;
import com.distributed.chat.system.kafka.consumer.service.ChatMessageDeliveryService;
import com.distributed.chat.system.mongodb.collection.ChatMessage.Attachment;
import com.distributed.chat.system.mongodb.collection.ChatMessage.Reaction;
import com.distributed.chat.system.mongodb.repository.ChatMessageRepository;
import com.google.protobuf.InvalidProtocolBufferException;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.bson.types.ObjectId;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.KafkaHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.stereotype.Component;
import protobuf.EdaChatMessage.ChatMessage;
import protobuf.EdaChatMessage.ChatReaction;

/**
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
    private final ConsumerMetrics consumerMetrics;

    @KafkaListener(topics = ChatTopics.CHAT_SEND_MESSAGE, groupId = "chat-group-1")
    public void consumeChatSendMessage(byte[] payload, @Header(KafkaHeaders.RECEIVED_TOPIC) String topic) {
        countRetry(topic, ChatTopics.CHAT_SEND_MESSAGE);
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

        // 1. 첨부파일 확인 (GridFS metadata). 남의 파일 / 다른 채널 파일은 버린다
        List<Attachment> attachments = chatAttachmentResolver.resolve(chatMessage);
        if (chatMessage.getMessage().isBlank() && attachments.isEmpty()) {
            // 첨부만 보냈는데 전부 버려졌으면 남는 게 없다
            log.warn("[chat-send-message] empty message dropped: sendUserId={}, channelId={}",
                chatMessage.getSendUserId(), chatMessage.getChannelId());
            return;
        }

        // 2. 이력 저장 (eventId 로 한 번만. 재시도 / 중복 발행이면 처음 저장된 문서를 그대로 받는다)
        com.distributed.chat.system.mongodb.collection.ChatMessage saved = consumerMetrics.timeSave(() ->
            chatMessageRepository.saveOnce(com.distributed.chat.system.mongodb.collection.ChatMessage.builder()
                .eventId(eventId)
                .channelId(chatMessage.getChannelId())
                .content(chatMessage.getMessage())
                .eventPublishDt(Instant.ofEpochMilli(chatMessage.getPublishDt()))
                .createDt(Instant.now())
                .createUserId(chatMessage.getSendUserId())
                .attachments(attachments.isEmpty() ? null : attachments)
                .build()));

        // 3. 수신자가 접속한 채팅 서버로 전달 (실패하면 예외 → 재시도 토픽)
        consumerMetrics.timeDelivery("message", () ->
            chatMessageDeliveryService.deliver(saved.getId().toHexString(), chatMessage, attachments));
    }

    /**
     * 이모지 리액션 : 메시지 문서의 reactions 를 원자적으로 바꾸고, 바뀐 뒤의 reactions 전체를 채널 접속자에게 보낸다.
     * $addToSet / $pull 이라 같은 이벤트를 다시 처리해도 결과가 같아서 메시지처럼 eventId 가 필요 없다
     */
    @KafkaListener(topics = ChatTopics.CHAT_REACTION, groupId = "chat-group-1")
    public void consumeChatReaction(byte[] payload, @Header(KafkaHeaders.RECEIVED_TOPIC) String topic) {
        countRetry(topic, ChatTopics.CHAT_REACTION);
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
        if (reactions.isEmpty()) {
            // 메시지가 없거나 다른 채널의 메시지면 버린다
            return;
        }
        consumerMetrics.reaction(chatReaction.getAction().name().toLowerCase());

        consumerMetrics.timeDelivery("reaction", () -> chatMessageDeliveryService.deliverReaction(
            chatReaction.getChannelId(), messageId.toHexString(), reactions.get()));
    }

    private void countRetry(String receivedTopic, String mainTopic) {
        if (receivedTopic != null && !receivedTopic.equals(mainTopic)) {
            consumerMetrics.retried(mainTopic);
        }
    }
}
