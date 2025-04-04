package com.distributed.chat.system.kafka.producer.web.controller;

import com.distributed.chat.system.kafka.producer.web.service.EventProducerService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import protobuf.EdaChatMessage.ChatMessage;

@RestController
@RequestMapping("/v1/private/event")
@RequiredArgsConstructor
@Slf4j
public class EventProducerController {

    private final EventProducerService eventProducerService;

    @PostMapping("/publish/chat-send-message")
    public void publishChatSendMessage(ChatMessage chatMessage) {
        eventProducerService.publishChatSendMessage(chatMessage);
    }
}
