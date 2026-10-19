package com.distributed.chat.system.message.consumer.feign;

import feign.Request;
import feign.Retryer;
import java.util.concurrent.TimeUnit;
import org.springframework.context.annotation.Bean;

/**
 * {@link KafkaProducerClient} 전용 설정. 전역 설정이 되지 않도록 @Configuration 을 붙이지 않는다.
 * <p>
 * 알림은 유실을 허용하므로 (놓친 메시지는 앱을 열면 MongoDB 에서 받는다) 타임아웃은 짧게 두고 재시도하지 않는다.
 * 호출은 알림 전용 스레드에서 하므로 느려져도 컨슈머 스레드(파티션)는 기다리지 않는다
 */
public class KafkaProducerClientConfig {

    private static final long CONNECT_TIMEOUT_MS = 500;
    private static final long READ_TIMEOUT_MS = 2000;

    @Bean
    public Request.Options kafkaProducerRequestOptions() {
        return new Request.Options(CONNECT_TIMEOUT_MS, TimeUnit.MILLISECONDS, READ_TIMEOUT_MS, TimeUnit.MILLISECONDS, true);
    }

    @Bean
    public Retryer kafkaProducerRetryer() {
        return Retryer.NEVER_RETRY;
    }
}
