package com.distributed.chat.system.kafka.consumer.consumer;

import com.distributed.chat.system.kafka.consumer.service.ChatMessageDeliveryService;
import com.distributed.chat.system.mongodb.repository.ChatMessageRepository;
import com.google.protobuf.InvalidProtocolBufferException;
import java.time.Instant;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;
import protobuf.EdaChatMessage.ChatMessage;

@Component
@Slf4j
@RequiredArgsConstructor
public class EventConsumer {

    private final ChatMessageRepository chatMessageRepository;
    private final ChatMessageDeliveryService chatMessageDeliveryService;

    @KafkaListener(topics = "chat-send-message", groupId = "chat-group-1")
    public void consumeChatSendMessage(byte[] payload) {
        ChatMessage chatMessage;
        try {
            chatMessage = ChatMessage.parseFrom(payload);
        } catch (InvalidProtocolBufferException e) {
            // 재시도해도 파싱되지 않는 메시지이므로 버린다
            log.error("[chat-send-message] invalid payload : {}", e.getMessage());
            return;
        }
        log.info("[chat-send-message] consumed: sendUserId={}, channelId={}",
            chatMessage.getSendUserId(), chatMessage.getChannelId());

        // 1. 이력 저장
        com.distributed.chat.system.mongodb.collection.ChatMessage saved = chatMessageRepository.save(
            com.distributed.chat.system.mongodb.collection.ChatMessage.builder()
                .channelId(chatMessage.getChannelId())
                .content(chatMessage.getMessage())
                .eventPublishDt(Instant.ofEpochMilli(chatMessage.getPublishDt()))
                .createDt(Instant.now())
                .createUserId(chatMessage.getSendUserId())
                .build()
        );

        // 2. 수신자가 접속한 채팅 서버로 전달
        chatMessageDeliveryService.deliver(saved.getId(), chatMessage);
    }
}
