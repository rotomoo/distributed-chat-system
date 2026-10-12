package com.distributed.chat.system.chatting.web.controller.feign;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.PostMapping;
import protobuf.EdaChatMessage.ChatMessage;

@FeignClient(name = "kafka-producer", path = "/v1/private/event")
public interface KafkaProducerClient {

    @PostMapping(value = "/publish/chat-send-message", consumes = "application/x-protobuf")
    void publishChatSendMessage(ChatMessage message);
}
