package com.distributed.chat.system.message.consumer.feign;

import com.distributed.chat.system.common.event.ChatNotificationEvent;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.PostMapping;

/**
 * 오프라인 멤버 알림 이벤트는 kafka-producer 가 발행한다 (발행은 kafka-producer 한 곳에서만. 채팅 서버와 같은 API).
 * kafka-producer 는 여러 대여도 아무 한 대면 되므로 Eureka 이름(name)으로 로드밸런싱한다.
 * 본문은 Spring 기본 JSON 인코더로 보낸다 (application/json, 이벤트 형식은 common EventJson 의 호환 규칙)
 */
@FeignClient(name = "kafka-producer", path = "/v1/private/event", configuration = KafkaProducerClientConfig.class)
public interface KafkaProducerClient {

    @PostMapping(value = "/publish/chat-notification", consumes = MediaType.APPLICATION_JSON_VALUE)
    void publishChatNotification(ChatNotificationEvent notification);
}
