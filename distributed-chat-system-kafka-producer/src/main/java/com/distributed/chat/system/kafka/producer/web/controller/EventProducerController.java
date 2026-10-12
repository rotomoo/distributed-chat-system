package com.distributed.chat.system.kafka.producer.web.controller;

import com.distributed.chat.system.kafka.producer.web.service.EventProducerService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import protobuf.EdaChatMessage.ChatMessage;

@RestController
@RequestMapping("/v1/private/event")
@RequiredArgsConstructor
@Slf4j
public class EventProducerController {

    private final EventProducerService eventProducerService;

    @PostMapping(value = "/publish/chat-send-message", consumes = "application/x-protobuf")
    public void publishChatSendMessage(@RequestBody ChatMessage chatMessage) {
        eventProducerService.publishChatSendMessage(chatMessage);
    }
}
