package com.distributed.chat.system.kafka.consumer.base.metrics;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import java.util.function.Supplier;
import org.springframework.stereotype.Component;

/**
 * 컨슈머 메트릭 (부하 테스트 / 모니터링용, /actuator/prometheus 에서 chat_consumer_* 로 보인다)
 * <ul>
 *   <li>chat.consumer.save : 메시지 저장 시간</li>
 *   <li>chat.consumer.delivery{type} : 채팅 서버 전달 시간 (message / reaction, 실패 포함)</li>
 *   <li>chat.consumer.retries{topic} : 재시도 토픽에서 다시 처리한 수</li>
 *   <li>chat.consumer.delivery.dropped{type} : 저장은 됐지만 끝내 전달 못 하고 버린 수 (클라이언트가 다시 조회해서 받는다)</li>
 *   <li>chat.consumer.dead.letters{type} : 저장도 못 하고 DLT 에 남은 수 (다시 발행해야 함)</li>
 *   <li>chat.consumer.reactions{action} : 처리한 리액션 수</li>
 * </ul>
 */
@Component
public class ConsumerMetrics {

    private final MeterRegistry meterRegistry;
    private final Timer saveTimer;

    public ConsumerMetrics(MeterRegistry meterRegistry) {
        this.meterRegistry = meterRegistry;
        this.saveTimer = Timer.builder("chat.consumer.save").description("메시지 저장 시간").register(meterRegistry);
    }

    public <T> T timeSave(Supplier<T> save) {
        return saveTimer.record(save);
    }

    public void timeDelivery(String type, Runnable delivery) {
        Timer.builder("chat.consumer.delivery").description("채팅 서버 전달 시간").tag("type", type)
            .register(meterRegistry)
            .record(delivery);
    }

    public void retried(String topic) {
        counter("chat.consumer.retries", "topic", topic).increment();
    }

    public void deliveryDropped(String type) {
        counter("chat.consumer.delivery.dropped", "type", type).increment();
    }

    public void deadLetter(String type) {
        counter("chat.consumer.dead.letters", "type", type).increment();
    }

    public void reaction(String action) {
        counter("chat.consumer.reactions", "action", action).increment();
    }

    private Counter counter(String name, String tagKey, String tagValue) {
        return Counter.builder(name).tag(tagKey, tagValue).register(meterRegistry);
    }
}
