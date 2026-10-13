package com.distributed.chat.system.kafka.producer.web.service;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import lombok.RequiredArgsConstructor;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;
import protobuf.EdaChatMessage.ChatMessage;
import protobuf.EdaChatMessage.ChatReaction;

/**
 * 메트릭 chat.producer.publish{topic, result} : 보낸 순간부터 브로커 응답(ack)까지 걸린 시간과 성공 / 실패 수 (_count)
 */
@Component
@RequiredArgsConstructor
public class EventProducerService {

    public static final String CHAT_SEND_MESSAGE_TOPIC = "chat-send-message";
    public static final String CHAT_REACTION_TOPIC = "chat-reaction";

    private final KafkaTemplate<String, byte[]> kafkaTemplate;
    private final MeterRegistry meterRegistry;

    public void publishChatSendMessage(ChatMessage chatMessage) {
        // 채널 ID 를 키로 사용하여 같은 채널의 메시지는 같은 파티션으로 -> 채널 내 순서 보장
        send(CHAT_SEND_MESSAGE_TOPIC, String.valueOf(chatMessage.getChannelId()), chatMessage.toByteArray());
    }

    public void publishChatReaction(ChatReaction chatReaction) {
        // 메시지와 같이 채널 ID 키 -> 한 채널의 리액션은 한 파티션에서 차례로 처리 (같은 메시지 갱신이 섞이지 않음)
        send(CHAT_REACTION_TOPIC, String.valueOf(chatReaction.getChannelId()), chatReaction.toByteArray());
    }

    private void send(String topic, String key, byte[] value) {
        Timer.Sample sample = Timer.start(meterRegistry);
        kafkaTemplate.send(topic, key, value).whenComplete((result, exception) -> sample.stop(Timer.builder("chat.producer.publish")
            .description("Kafka 발행 시간 (ack 까지)")
            .tag("topic", topic)
            .tag("result", exception == null ? "success" : "failure")
            .register(meterRegistry)));
    }
}
