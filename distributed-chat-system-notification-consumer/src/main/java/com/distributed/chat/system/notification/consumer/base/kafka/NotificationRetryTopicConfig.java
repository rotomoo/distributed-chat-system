package com.distributed.chat.system.notification.consumer.base.kafka;

import com.distributed.chat.system.common.event.MalformedEventException;
import com.distributed.chat.system.common.kafka.ChatNotificationTopic;
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
 * message-consumer 와 같은 막히지 않는 재시도 (재시도 토픽 + DLT). 알림은 늦게 가면 의미가 줄어서 짧게 몇 번만 한다
 * (기본 2초 간격 3번 시도 → chat-notification-retry → chat-notification-dlt, {@link NotificationDeadLetterHandler} 는 로그만).
 * 읽을 수 없는 값({@link MalformedEventException})은 다시 해도 같으므로 재시도 없이 바로 DLT
 */
@Configuration
@EnableKafkaRetryTopic
public class NotificationRetryTopicConfig {

    @Bean
    public RetryTopicConfiguration notificationRetryTopicConfiguration(
        KafkaTemplate<?, ?> kafkaTemplate,
        @Value("${chat.notification.retry.delay-ms:2000}") long delayMs,
        @Value("${chat.notification.retry.max-attempts:3}") int maxAttempts,
        @Value("${chat.notification.retry.topic-partitions:3}") int topicPartitions
    ) {
        return RetryTopicConfigurationBuilder.newInstance()
            .includeTopic(ChatNotificationTopic.TOPIC)
            .maxAttempts(maxAttempts)
            .fixedBackOff(delayMs)
            .notRetryOn(MalformedEventException.class)
            .autoCreateTopicsWith(topicPartitions, (short) -1)
            .dltHandlerMethod(NotificationDeadLetterHandler.BEAN_NAME, "handle")
            .create(kafkaTemplate);
    }

    /**
     * 재시도 토픽이 대기 시간 뒤 파티션을 다시 여는 데 쓰는 스케줄러 (이 용도로만 따로 둔다)
     */
    @Bean
    public RetryTopicSchedulerWrapper retryTopicSchedulerWrapper() {
        ThreadPoolTaskScheduler scheduler = new ThreadPoolTaskScheduler();
        scheduler.setThreadNamePrefix("notification-retry-");
        scheduler.initialize();
        return new RetryTopicSchedulerWrapper(scheduler);
    }
}
