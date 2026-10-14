package com.distributed.chat.system.kafka.producer.web.controller;

import com.distributed.chat.system.common.event.MalformedEventException;
import com.distributed.chat.system.kafka.producer.web.service.EventProducerService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * 본문은 이벤트 JSON 문자열 그대로 받는다 (record 로 읽었다가 다시 쓰면 이 서버가 모르는 새 필드가 빠진다)
 */
@RestController
@RequestMapping("/v1/private/event")
@RequiredArgsConstructor
@Slf4j
public class EventProducerController {

    private final EventProducerService eventProducerService;

    /**
     * 채팅 메시지 발행 (본문 = {@link com.distributed.chat.system.common.event.ChatMessageEvent} JSON)
     */
    @PostMapping(value = "/publish/chat-send-message", consumes = MediaType.APPLICATION_JSON_VALUE)
    public void publishChatSendMessage(@RequestBody String payload) {
        eventProducerService.publishChatSendMessage(payload);
    }

    /**
     * 이모지 리액션 추가/제거 발행 (본문 = {@link com.distributed.chat.system.common.event.ChatReactionEvent} JSON)
     */
    @PostMapping(value = "/publish/chat-reaction", consumes = MediaType.APPLICATION_JSON_VALUE)
    public void publishChatReaction(@RequestBody String payload) {
        eventProducerService.publishChatReaction(payload);
    }

    /**
     * 이벤트로 읽을 수 없는 본문은 발행하지 않고 400
     */
    @ExceptionHandler(MalformedEventException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public void malformedEvent(MalformedEventException e) {
        log.warn("malformed event, not published : {}", e.getMessage());
    }
}
