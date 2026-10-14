package com.distributed.chat.system.chatting.web.controller.feign;

import com.distributed.chat.system.common.event.ChatMessageEvent;
import com.distributed.chat.system.common.event.ChatReactionEvent;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.PostMapping;

/**
 * 본문은 Spring 기본 JSON 인코더로 보낸다 (application/json, 이벤트 형식은 common EventJson 의 호환 규칙)
 */
@FeignClient(name = "kafka-producer", path = "/v1/private/event")
public interface KafkaProducerClient {

    @PostMapping(value = "/publish/chat-send-message", consumes = MediaType.APPLICATION_JSON_VALUE)
    void publishChatSendMessage(ChatMessageEvent message);

    @PostMapping(value = "/publish/chat-reaction", consumes = MediaType.APPLICATION_JSON_VALUE)
    void publishChatReaction(ChatReactionEvent reaction);
}
