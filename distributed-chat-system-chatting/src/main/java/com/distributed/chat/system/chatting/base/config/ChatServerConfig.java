package com.distributed.chat.system.chatting.base.config;

import com.distributed.chat.system.chatting.base.metrics.ChatServerMetrics;
import io.micrometer.core.instrument.MeterRegistry;
import java.time.Clock;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * 하트비트 스케줄러, 시계(테스트에서 바꿔 끼울 수 있게), 메트릭
 */
@Configuration
@EnableScheduling
public class ChatServerConfig {

    @Bean
    public Clock clock() {
        return Clock.systemUTC();
    }

    @Bean
    public ChatServerMetrics chatServerMetrics(MeterRegistry meterRegistry) {
        return new ChatServerMetrics(meterRegistry);
    }
}
