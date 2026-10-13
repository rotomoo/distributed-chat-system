package com.distributed.chat.system.client.api.base.actuator;

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

@DisplayName("[통합] client-api 메트릭 엔드포인트 테스트")
@SpringBootTest(properties = "eureka.client.enabled=false")
@AutoConfigureMockMvc
@AutoConfigureObservability
class ActuatorIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    @DisplayName("success case : 로그인 없이 /actuator/prometheus 로 HTTP 요청 메트릭을 볼 수 있다")
    void 로그인_없이_actuator_prometheus_로_HTTP_요청_메트릭을_볼_수_있다() throws Exception {
        // given
        mockMvc.perform(get("/actuator/health")).andExpect(status().isOk());

        // when & then
        mockMvc.perform(get("/actuator/prometheus"))
            .andExpect(status().isOk())
            .andExpect(content().string(Matchers.containsString("http_server_requests_seconds")))
            .andExpect(content().string(Matchers.containsString("application=\"client-api\"")));
    }

    @Test
    @DisplayName("fail case : health, prometheus 외의 actuator 엔드포인트는 열지 않는다")
    void health_prometheus_외의_actuator_엔드포인트는_열지_않는다() throws Exception {
        // when & then
        mockMvc.perform(get("/actuator/env")).andExpect(status().isNotFound());
        mockMvc.perform(get("/actuator/beans")).andExpect(status().isNotFound());
    }
}
