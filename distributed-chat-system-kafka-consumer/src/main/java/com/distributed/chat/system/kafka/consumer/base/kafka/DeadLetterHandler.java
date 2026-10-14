package com.distributed.chat.system.kafka.consumer.base.kafka;

import com.distributed.chat.system.common.event.ChatMessageEvent;
import com.distributed.chat.system.common.event.ChatReactionEvent;
import com.distributed.chat.system.common.event.EventJson;
import com.distributed.chat.system.common.event.MalformedEventException;
import com.distributed.chat.system.kafka.consumer.service.DeliveryFailedException;
import com.distributed.chat.system.mongodb.repository.ChatMessageRepository;
import java.nio.charset.StandardCharsets;
import java.util.stream.Stream;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.common.header.Header;
import org.springframework.kafka.support.KafkaHeaders;
import org.springframework.stereotype.Component;

/**
 * 재시도를 다 써도 실패한 레코드 (DLT).
 * <ul>
 *   <li>저장은 됐고 전달만 실패 → WARN 로그 (전달 버림. 다시 발행할 필요 없음, 클라이언트가 재접속 / 빈틈 채우기로 받는다)</li>
 *   <li>저장도 못 함 → ERROR 로그 (dead letter) 를 남기고 DLT 에 그대로 둔다 (원인을 고친 뒤 아래처럼 다시 발행)</li>
 *   <li>이벤트로 읽을 수 없음 (JSON 이 아님, 예전 바이너리 형식으로 남은 값 등) → 재시도 없이 바로 여기로 온다. ERROR 로그 (malformed) 를 남기고
 *       DLT 에 그대로 둔다. 그대로 다시 발행해도 또 실패하므로 보낸 쪽을 고치거나 값을 고쳐서 발행한다</li>
 * </ul>
 * DLT 토픽 / 쌓인 수 / consumer lag 은 kafka-ui 에서 본다
 * 메시지는 eventId 로 저장 여부를 확인한다. 리액션은 저장 결과를 따로 남기지 않으므로 마지막 예외가 전달 실패인지로 판단한다
 * (리액션 갱신은 $addToSet / $pull 이라 다시 해도 결과가 같아 별도 키가 필요 없다)
 * <p>
 * <b>DLT 다시 발행하기</b> (원인을 고친 뒤). 메시지는 같은 eventId 라 이미 저장된 것은 다시 저장되지 않고, 리액션은 다시 해도 결과가 같다.
 * 키(channelId)를 그대로 유지해야 채널 순서가 지켜진다. 값은 이벤트 JSON 문자열이라 kafka-ui / 콘솔에서 그대로 읽힌다
 * <pre>
 * # 1) DLT 내용 확인 (ERROR 로그의 partition / offset 과 맞춰 본다)
 * docker compose exec kafka-1 /opt/kafka/bin/kafka-console-consumer.sh --bootstrap-server kafka-1:9192 \
 *   --topic chat-send-message-dlt --from-beginning --property print.key=true --property print.offset=true
 *
 * # 2) 원래 토픽으로 다시 보낸다 (kcat, 호스트에서 localhost:9194)
 * kcat -b localhost:9194 -C -t chat-send-message-dlt -o beginning -e -f '%k\t%s\n' \
 *   | kcat -b localhost:9194 -P -t chat-send-message -K '\t'
 *
 * # 리액션은 chat-reaction-dlt → chat-reaction 으로 같은 방법
 * </pre>
 * 특정 레코드만 다시 보내려면 kcat -C 에 -p {partition} -o {offset} -c 1 을 준다
 */
@Component(DeadLetterHandler.BEAN_NAME)
@Slf4j
@RequiredArgsConstructor
public class DeadLetterHandler {

    public static final String BEAN_NAME = "deadLetterHandler";

    private final ChatMessageRepository chatMessageRepository;

    /**
     * DLT 에서 받은 레코드의 결과
     */
    public enum Outcome {
        /**
         * 저장 / 반영은 됐고 전달만 못 함 (다시 발행할 필요 없음)
         */
        DELIVERY_DROPPED,
        /**
         * 저장 / 반영도 못 함 (다시 발행 대상)
         */
        DEAD_LETTER,
        /**
         * 이벤트로 읽을 수 없음 (그대로 다시 발행해도 같다)
         */
        MALFORMED
    }

    public void handle(ConsumerRecord<String, String> record) {
        String reason = header(record, KafkaHeaders.EXCEPTION_MESSAGE, "");
        Outcome outcome = classify(record);
        String detail = describe(record);
        switch (outcome) {
            case DELIVERY_DROPPED -> log.warn(
                "[dlt] delivery dropped (saved, no replay needed) {}, dltTopic : {}, partition : {}, offset : {}, reason : {}",
                detail, record.topic(), record.partition(), record.offset(), reason);
            case MALFORMED -> log.error(
                "[dlt] malformed payload (NOT saved, replay as-is fails again) {}, dltTopic : {}, partition : {}, offset : {}, reason : {}",
                detail, record.topic(), record.partition(), record.offset(), reason);
            default -> log.error(
                "[dlt] dead letter (NOT saved, replay needed) {}, dltTopic : {}, partition : {}, offset : {}, reason : {}",
                detail, record.topic(), record.partition(), record.offset(), reason);
        }
    }

    /**
     * 읽을 수 없으면 MALFORMED. 메시지는 eventId 로 저장됐는지 확인, 리액션은 마지막 예외가 전달 실패인지로 판단한다
     */
    public Outcome classify(ConsumerRecord<String, String> record) {
        String originalTopic = originalTopic(record);
        try {
            switch (originalTopic) {
                case ChatTopics.CHAT_SEND_MESSAGE -> {
                    String eventId = ChatTopics.eventIdOf(EventJson.read(record.value(), ChatMessageEvent.class));
                    return chatMessageRepository.findByEventId(eventId).isPresent() ? Outcome.DELIVERY_DROPPED : Outcome.DEAD_LETTER;
                }
                case ChatTopics.CHAT_REACTION -> {
                    EventJson.read(record.value(), ChatReactionEvent.class);
                    return failedOnDelivery(record) ? Outcome.DELIVERY_DROPPED : Outcome.DEAD_LETTER;
                }
                default -> {
                    return Outcome.DEAD_LETTER;
                }
            }
        } catch (MalformedEventException e) {
            return Outcome.MALFORMED;
        }
    }

    /**
     * 로그용 : 다시 발행할 때 찾을 수 있는 값들
     */
    private static String describe(ConsumerRecord<String, String> record) {
        String originalTopic = originalTopic(record);
        try {
            if (ChatTopics.CHAT_SEND_MESSAGE.equals(originalTopic)) {
                ChatMessageEvent message = EventJson.read(record.value(), ChatMessageEvent.class);
                return "topic : " + originalTopic + ", eventId : " + ChatTopics.eventIdOf(message) + ", channelId : " + message.channelId()
                    + ", sendUserId : " + message.sendUserId() + ", publishDt : " + message.publishDt();
            }
            if (ChatTopics.CHAT_REACTION.equals(originalTopic)) {
                ChatReactionEvent reaction = EventJson.read(record.value(), ChatReactionEvent.class);
                return "topic : " + originalTopic + ", messageId : " + reaction.messageId() + ", channelId : " + reaction.channelId()
                    + ", sendUserId : " + reaction.sendUserId() + ", emoji : " + reaction.emoji() + ", action : " + reaction.action();
            }
        } catch (MalformedEventException e) {
            return "topic : " + originalTopic + ", unreadable payload (" + e.getMessage() + ")";
        }
        return "topic : " + originalTopic;
    }

    private static String originalTopic(ConsumerRecord<String, String> record) {
        return ChatTopics.mainTopicOf(header(record, KafkaHeaders.ORIGINAL_TOPIC, record.topic()));
    }

    /**
     * 마지막 예외가 전달 실패인지. 재시도 토픽은 예외를 한두 겹 감싸므로(ListenerExecutionFailedException 등)
     * 예외 / 원인 클래스 헤더와 스택 트레이스 헤더를 모두 본다
     */
    private static boolean failedOnDelivery(ConsumerRecord<String, String> record) {
        String deliveryFailed = DeliveryFailedException.class.getName();
        return Stream.of(KafkaHeaders.EXCEPTION_FQCN, KafkaHeaders.EXCEPTION_CAUSE_FQCN, KafkaHeaders.EXCEPTION_STACKTRACE)
            .anyMatch(name -> header(record, name, "").contains(deliveryFailed));
    }

    private static String header(ConsumerRecord<String, String> record, String name, String defaultValue) {
        Header header = record.headers().lastHeader(name);
        return header == null ? defaultValue : new String(header.value(), StandardCharsets.UTF_8);
    }
}
