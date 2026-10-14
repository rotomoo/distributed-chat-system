package com.distributed.chat.system.chatting.web.controller;

import com.distributed.chat.system.chatting.base.handler.CustomTextWebSocketHandler;
import com.distributed.chat.system.common.event.ChatMessageDelivery;
import com.distributed.chat.system.common.event.ChatReactionDelivery;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 서버간 내부 통신용 (컨슈머가 JSON 으로 부른다). api-gateway 는 /v1/**, /websocket/** 만 라우팅하므로 외부에 노출되지 않는다.
 */
@RestController
@RequestMapping("/internal/chat")
@RequiredArgsConstructor
public class ChatDeliveryController {

    private final CustomTextWebSocketHandler customTextWebSocketHandler;

    @PostMapping(value = "/deliver", consumes = MediaType.APPLICATION_JSON_VALUE)
    public void deliver(@RequestBody ChatMessageDelivery delivery) {
        customTextWebSocketHandler.deliver(delivery);
    }

    /**
     * 이모지 리액션이 바뀐 메시지의 reactions 전체
     */
    @PostMapping(value = "/deliver-reaction", consumes = MediaType.APPLICATION_JSON_VALUE)
    public void deliverReaction(@RequestBody ChatReactionDelivery delivery) {
        customTextWebSocketHandler.deliverReaction(delivery);
    }
}
