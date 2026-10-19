package com.distributed.chat.system.message.consumer.service;

import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 알림 발행 요청(kafka-producer HTTP)을 보내는 스레드. 컨슈머 스레드는 넣기만 하고 기다리지 않는다.
 * 대기열이 가득 차면 (kafka-producer 가 느리거나 죽음) 넣지 못한 알림은 버린다 (RejectedExecutionException → {@link ChatNotificationPublisher} 가 로그)
 */
@Configuration
public class NotificationExecutorConfig {

    public static final String EXECUTOR = "chatNotificationExecutor";

    @Bean(name = EXECUTOR, destroyMethod = "shutdown")
    public ExecutorService chatNotificationExecutor(
        @Value("${chat.notification.parallelism:2}") int parallelism,
        @Value("${chat.notification.queue-capacity:10000}") int queueCapacity) {
        AtomicInteger sequence = new AtomicInteger();
        return new ThreadPoolExecutor(parallelism, parallelism, 0L, TimeUnit.MILLISECONDS, new ArrayBlockingQueue<>(queueCapacity),
            runnable -> {
                Thread thread = new Thread(runnable, "chat-notification-" + sequence.incrementAndGet());
                thread.setDaemon(true);
                return thread;
            },
            new ThreadPoolExecutor.AbortPolicy());
    }
}
