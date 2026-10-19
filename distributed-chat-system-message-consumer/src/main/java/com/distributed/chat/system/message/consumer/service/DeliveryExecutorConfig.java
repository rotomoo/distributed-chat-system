package com.distributed.chat.system.message.consumer.service;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 여러 채팅 서버로 동시에 보내는 스레드 (수가 정해진 풀). 컨슈머 스레드는 모두 끝날 때까지 기다린다
 */
@Configuration
public class DeliveryExecutorConfig {

    public static final String EXECUTOR = "chatDeliveryExecutor";

    @Bean(name = EXECUTOR, destroyMethod = "shutdown")
    public ExecutorService chatDeliveryExecutor(@Value("${chat.delivery.parallelism:8}") int parallelism) {
        AtomicInteger sequence = new AtomicInteger();
        return Executors.newFixedThreadPool(parallelism, runnable -> {
            Thread thread = new Thread(runnable, "chat-delivery-" + sequence.incrementAndGet());
            thread.setDaemon(true);
            return thread;
        });
    }
}
