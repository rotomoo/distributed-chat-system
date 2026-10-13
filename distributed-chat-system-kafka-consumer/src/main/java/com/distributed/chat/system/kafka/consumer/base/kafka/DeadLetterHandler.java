package com.distributed.chat.system.kafka.consumer.base.kafka;

import com.distributed.chat.system.kafka.consumer.base.metrics.ConsumerMetrics;
import com.distributed.chat.system.kafka.consumer.service.DeliveryFailedException;
import com.distributed.chat.system.mongodb.repository.ChatMessageRepository;
import com.google.protobuf.InvalidProtocolBufferException;
import java.nio.charset.StandardCharsets;
import java.util.stream.Stream;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.common.header.Header;
import org.springframework.kafka.support.KafkaHeaders;
import org.springframework.stereotype.Component;
import protobuf.EdaChatMessage.ChatMessage;
import protobuf.EdaChatMessage.ChatReaction;

/**
 * 재시도를 다 써도 실패한 레코드 (DLT).
 * <ul>
 *   <li>저장은 됐고 전달만 실패 → "전달 버림" 으로 세고 끝 (다시 발행할 필요 없음. 클라이언트가 재접속 / 빈틈 채우기로 받는다)</li>
 *   <li>저장도 못 함 → ERROR 로그 + "dead letter" 로 세고 DLT 에 그대로 둔다 (원인을 고친 뒤 다시 발행, README 모니터링 참고)</li>
 * </ul>
 * 메시지는 eventId 로 저장 여부를 확인한다. 리액션은 저장 결과를 따로 남기지 않으므로 마지막 예외가 전달 실패인지로 판단한다
 * (리액션 갱신은 $addToSet / $pull 이라 다시 해도 결과가 같아 별도 키가 필요 없다)
 * <p>
 * <b>DLT 다시 발행하기</b> (원인을 고친 뒤). 메시지는 같은 eventId 라 이미 저장된 것은 다시 저장되지 않고, 리액션은 다시 해도 결과가 같다.
 * 키(channelId)를 그대로 유지해야 채널 순서가 지켜진다. 값은 protobuf 바이트이므로 바이너리를 그대로 옮긴다
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
    private final ConsumerMetrics consumerMetrics;

    public void handle(ConsumerRecord<String, byte[]> record) {
        String originalTopic = ChatTopics.mainTopicOf(header(record, KafkaHeaders.ORIGINAL_TOPIC, record.topic()));
        String reason = header(record, KafkaHeaders.EXCEPTION_MESSAGE, "");

        switch (originalTopic) {
            case ChatTopics.CHAT_SEND_MESSAGE -> handleChatMessage(record, reason);
            case ChatTopics.CHAT_REACTION -> handleReaction(record, reason);
            default -> {
                consumerMetrics.deadLetter("unknown");
                log.error("[dlt] unknown topic : {}, offset : {}, reason : {}", record.topic(), record.offset(), reason);
            }
        }
    }

    private void handleChatMessage(ConsumerRecord<String, byte[]> record, String reason) {
        ChatMessage chatMessage;
        try {
            chatMessage = ChatMessage.parseFrom(record.value());
        } catch (InvalidProtocolBufferException e) {
            consumerMetrics.deadLetter("message");
            log.error("[dlt] chat-send-message unreadable payload offset : {}, reason : {}", record.offset(), reason);
            return;
        }

        String eventId = ChatTopics.eventIdOf(chatMessage);
        if (chatMessageRepository.findByEventId(eventId).isPresent()) {
            consumerMetrics.deliveryDropped("message");
            log.warn("[dlt] chat-send-message saved but delivery dropped eventId : {}, channelId : {}, reason : {}",
                eventId, chatMessage.getChannelId(), reason);
            return;
        }
        consumerMetrics.deadLetter("message");
        log.error("[dlt] chat-send-message NOT saved (replay needed) eventId : {}, channelId : {}, sendUserId : {}, publishDt : {}, "
                + "dltTopic : {}, partition : {}, offset : {}, reason : {}",
            eventId, chatMessage.getChannelId(), chatMessage.getSendUserId(), chatMessage.getPublishDt(),
            record.topic(), record.partition(), record.offset(), reason);
    }

    private void handleReaction(ConsumerRecord<String, byte[]> record, String reason) {
        if (failedOnDelivery(record)) {
            consumerMetrics.deliveryDropped("reaction");
            log.warn("[dlt] chat-reaction applied but delivery dropped offset : {}, reason : {}", record.offset(), reason);
            return;
        }
        consumerMetrics.deadLetter("reaction");
        String detail;
        try {
            ChatReaction reaction = ChatReaction.parseFrom(record.value());
            detail = "messageId : " + reaction.getMessageId() + ", channelId : " + reaction.getChannelId()
                + ", sendUserId : " + reaction.getSendUserId() + ", emoji : " + reaction.getEmoji() + ", action : " + reaction.getAction();
        } catch (InvalidProtocolBufferException e) {
            detail = "unreadable payload";
        }
        log.error("[dlt] chat-reaction NOT applied (replay needed) {}, dltTopic : {}, partition : {}, offset : {}, reason : {}",
            detail, record.topic(), record.partition(), record.offset(), reason);
    }

    /**
     * 마지막 예외가 전달 실패인지. 재시도 토픽은 예외를 한두 겹 감싸므로(ListenerExecutionFailedException 등)
     * 예외 / 원인 클래스 헤더와 스택 트레이스 헤더를 모두 본다
     */
    private static boolean failedOnDelivery(ConsumerRecord<String, byte[]> record) {
        String deliveryFailed = DeliveryFailedException.class.getName();
        return Stream.of(KafkaHeaders.EXCEPTION_FQCN, KafkaHeaders.EXCEPTION_CAUSE_FQCN, KafkaHeaders.EXCEPTION_STACKTRACE)
            .anyMatch(name -> header(record, name, "").contains(deliveryFailed));
    }

    private static String header(ConsumerRecord<String, byte[]> record, String name, String defaultValue) {
        Header header = record.headers().lastHeader(name);
        return header == null ? defaultValue : new String(header.value(), StandardCharsets.UTF_8);
    }
}
