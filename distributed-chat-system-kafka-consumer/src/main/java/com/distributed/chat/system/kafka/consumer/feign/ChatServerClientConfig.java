package com.distributed.chat.system.kafka.consumer.feign;

import feign.Request;
import feign.Retryer;
import java.util.concurrent.TimeUnit;
import org.springframework.context.annotation.Bean;

/**
 * {@link ChatServerClient} 전용 설정. 전역 설정이 되지 않도록 @Configuration 을 붙이지 않는다.
 * <p>
 * 컨슈머는 파티션을 한 스레드로 처리하므로 느린 채팅 서버 하나가 그 파티션의 모든 채널을 늦춘다. 그래서 타임아웃은 짧게 두고
 * Feign 재시도는 하지 않는다 (1번만 시도). 재시도는 Kafka 재시도 토픽이 1s → 5s → 30s 로 파티션을 막지 않고 한다.
 * 여기서도 재시도하면 실패한 서버 하나에 (타임아웃 × 시도 수) 만큼 파티션이 멈추고, 재시도 토픽 대기와 겹쳐 두 번 기다리게 된다
 */
public class ChatServerClientConfig {

    private static final long CONNECT_TIMEOUT_MS = 500;
    private static final long READ_TIMEOUT_MS = 2000;

    @Bean
    public Request.Options chatServerRequestOptions() {
        return new Request.Options(CONNECT_TIMEOUT_MS, TimeUnit.MILLISECONDS, READ_TIMEOUT_MS, TimeUnit.MILLISECONDS, true);
    }

    @Bean
    public Retryer chatServerRetryer() {
        return Retryer.NEVER_RETRY;
    }
}
