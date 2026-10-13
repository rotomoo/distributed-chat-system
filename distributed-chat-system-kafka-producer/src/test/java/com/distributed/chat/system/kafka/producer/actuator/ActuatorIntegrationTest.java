package com.distributed.chat.system.kafka.producer.actuator;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.hamcrest.Matchers;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.actuate.observability.AutoConfigureObservability;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;

@DisplayName("[통합] kafka-producer 메트릭 엔드포인트 테스트")
@SpringBootTest(properties = "eureka.client.enabled=false")
@AutoConfigureMockMvc
@AutoConfigureObservability
class ActuatorIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    @DisplayName("success case : /actuator/health 와 /actuator/prometheus 를 연다")
    void actuator_health_와_prometheus_를_연다() throws Exception {
        // when & then
        mockMvc.perform(get("/actuator/health")).andExpect(status().isOk());
        mockMvc.perform(get("/actuator/prometheus"))
            .andExpect(status().isOk())
            .andExpect(content().string(Matchers.containsString("application=\"kafka-producer\"")));
        mockMvc.perform(get("/actuator/env")).andExpect(status().isNotFound());
    }
}
