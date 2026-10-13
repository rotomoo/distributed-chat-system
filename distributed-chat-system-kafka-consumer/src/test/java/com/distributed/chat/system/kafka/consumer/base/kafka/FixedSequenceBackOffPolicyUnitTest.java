package com.distributed.chat.system.kafka.consumer.base.kafka;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.retry.backoff.BackOffContext;
import org.springframework.kafka.retrytopic.BackOffValuesGenerator;

@DisplayName("[단위] FixedSequenceBackOffPolicy 테스트")
class FixedSequenceBackOffPolicyUnitTest {

    @Test
    @DisplayName("success case : 정한 순서대로 1s → 5s → 30s 를 기다린다")
    void 정한_순서대로_기다린다() {
        // arrange
        List<Long> slept = new ArrayList<>();
        FixedSequenceBackOffPolicy policy = new FixedSequenceBackOffPolicy(List.of(1000L, 5000L, 30000L)).withSleeper(slept::add);
        BackOffContext context = policy.start(null);

        // act
        for (int i = 0; i < 3; i++) {
            policy.backOff(context);
        }

        // assert
        assertThat(slept).containsExactly(1000L, 5000L, 30000L);
    }

    @Test
    @DisplayName("success case : 재시도 토픽이 쓰는 대기 값도 1s, 5s, 30s 다 (토픽 3개)")
    void 재시도_토픽이_쓰는_대기_값도_1s_5s_30s_다() {
        // act
        List<Long> values = new BackOffValuesGenerator(4, new FixedSequenceBackOffPolicy(List.of(1000L, 5000L, 30000L))).generateValues();

        // assert
        assertThat(values).containsExactly(1000L, 5000L, 30000L);
    }
}
