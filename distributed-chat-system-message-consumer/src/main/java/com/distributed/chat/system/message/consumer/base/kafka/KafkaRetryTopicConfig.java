package com.distributed.chat.system.message.consumer.base.kafka;

import com.distributed.chat.system.common.event.MalformedEventException;
import java.util.List;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.annotation.EnableKafkaRetryTopic;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.retrytopic.RetryTopicConfiguration;
import org.springframework.kafka.retrytopic.RetryTopicConfigurationBuilder;
import org.springframework.kafka.retrytopic.RetryTopicSchedulerWrapper;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;

/**
 * 막히지 않는 재시도 (non-blocking retry) + DLT.
 * <p>
 * 실패한 레코드는 원래 파티션에서 바로 넘기고 재시도 토픽(chat-send-message-retry-1000, -5000, -30000)으로 옮겨 정해진 시간 뒤에
 * 다시 처리한다. 끝내 실패하면 DLT(chat-send-message-dlt)로 가고 {@link DeadLetterHandler} 가 받는다.
 * 그래서 한 메시지의 저장 / 전달 실패가 같은 파티션의 다른 채널 메시지를 막지 않는다.
 * <ul>
 *   <li>저장은 eventId 로 한 번만 되므로 다시 처리해도 문서가 늘지 않는다</li>
 *   <li>다시 전달된 메시지는 클라이언트가 messageId 로 걸러내고, 리액션은 전체 교체라 몇 번 받아도 같다</li>
 *   <li>대가 : 재시도로 늦게 저장된 메시지는 그 사이 저장된 메시지보다 뒤(_id)에 놓인다</li>
 * </ul>
 * 대기 시간은 chat.kafka.retry.delays-ms (기본 1000,5000,30000), 시도 수 = 대기 수 + 1.
 * 이벤트로 읽을 수 없는 값({@link MalformedEventException})은 다시 해도 같으므로 재시도 토픽을 거치지 않고 바로 DLT 로 보낸다
 * (파티션을 막거나 같은 실패를 되풀이하지 않고, 레코드는 DLT 에 남아 확인할 수 있다)
 */
@Configuration
@EnableKafkaRetryTopic
public class KafkaRetryTopicConfig {

    @Bean
    public RetryTopicConfiguration chatRetryTopicConfiguration(
        KafkaTemplate<?, ?> kafkaTemplate,
        @Value("${chat.kafka.retry.delays-ms:1000,5000,30000}") List<Long> delaysMs,
        @Value("${chat.kafka.retry.topic-partitions:3}") int topicPartitions
    ) {
        return RetryTopicConfigurationBuilder.newInstance()
            .includeTopics(ChatTopics.ALL)
            .maxAttempts(delaysMs.size() + 1)
            .customBackoff(new FixedSequenceBackOffPolicy(delaysMs))
            .notRetryOn(MalformedEventException.class)
            .autoCreateTopicsWith(topicPartitions, (short) -1)
            .dltHandlerMethod(DeadLetterHandler.BEAN_NAME, "handle")
            .create(kafkaTemplate);
    }

    /**
     * 재시도 토픽이 대기 시간 뒤 파티션을 다시 여는 데 쓰는 스케줄러 (이 용도로만 따로 둔다)
     */
    @Bean
    public RetryTopicSchedulerWrapper retryTopicSchedulerWrapper() {
        ThreadPoolTaskScheduler scheduler = new ThreadPoolTaskScheduler();
        scheduler.setThreadNamePrefix("kafka-retry-");
        scheduler.initialize();
        return new RetryTopicSchedulerWrapper(scheduler);
    }
}
