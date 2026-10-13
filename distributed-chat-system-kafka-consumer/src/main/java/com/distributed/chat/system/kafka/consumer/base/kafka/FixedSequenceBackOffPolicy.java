package com.distributed.chat.system.kafka.consumer.base.kafka;

import java.util.List;
import org.springframework.retry.RetryContext;
import org.springframework.retry.backoff.BackOffContext;
import org.springframework.retry.backoff.BackOffInterruptedException;
import org.springframework.retry.backoff.Sleeper;
import org.springframework.retry.backoff.SleepingBackOffPolicy;
import org.springframework.retry.backoff.ThreadWaitSleeper;

/**
 * 정해 둔 순서대로 기다린다 (예: 1s → 5s → 30s). 배수로는 1 → 5 → 25 처럼 원하는 값이 안 나와서 직접 둔다.
 * 재시도 토픽은 이 값들로 토픽마다 대기 시간을 정한다 (실제로 스레드를 재우지 않는다)
 */
public class FixedSequenceBackOffPolicy implements SleepingBackOffPolicy<FixedSequenceBackOffPolicy> {

    private final List<Long> delaysMs;
    private final Sleeper sleeper;

    public FixedSequenceBackOffPolicy(List<Long> delaysMs) {
        this(delaysMs, new ThreadWaitSleeper());
    }

    private FixedSequenceBackOffPolicy(List<Long> delaysMs, Sleeper sleeper) {
        if (delaysMs.isEmpty()) {
            throw new IllegalArgumentException("delaysMs must not be empty");
        }
        this.delaysMs = List.copyOf(delaysMs);
        this.sleeper = sleeper;
    }

    @Override
    public FixedSequenceBackOffPolicy withSleeper(Sleeper sleeper) {
        return new FixedSequenceBackOffPolicy(delaysMs, sleeper);
    }

    @Override
    public BackOffContext start(RetryContext context) {
        return new SequenceContext();
    }

    @Override
    public void backOff(BackOffContext backOffContext) throws BackOffInterruptedException {
        SequenceContext context = (SequenceContext) backOffContext;
        long delay = delaysMs.get(Math.min(context.attempt++, delaysMs.size() - 1));
        try {
            sleeper.sleep(delay);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new BackOffInterruptedException("interrupted", e);
        }
    }

    private static final class SequenceContext implements BackOffContext {

        private int attempt;
    }
}
