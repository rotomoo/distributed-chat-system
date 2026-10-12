package com.distributed.chat.system.chatting.web.controller;

import com.distributed.chat.system.chatting.base.handler.CustomTextWebSocketHandler;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import protobuf.EdaChatMessage.ChatMessageDelivery;

/**
 * 서버간 내부 통신용. api-gateway 는 /v1/**, /websocket/** 만 라우팅하므로 외부에 노출되지 않는다.
 */
@RestController
@RequestMapping("/internal/chat")
@RequiredArgsConstructor
public class ChatDeliveryController {

    private final CustomTextWebSocketHandler customTextWebSocketHandler;

    @PostMapping(value = "/deliver", consumes = "application/x-protobuf")
    public void deliver(@RequestBody ChatMessageDelivery delivery) {
        customTextWebSocketHandler.deliver(delivery);
    }
}
