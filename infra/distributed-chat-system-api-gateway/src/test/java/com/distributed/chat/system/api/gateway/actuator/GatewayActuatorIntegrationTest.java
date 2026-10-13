package com.distributed.chat.system.api.gateway.actuator;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.autoconfigure.actuate.observability.AutoConfigureObservability;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalManagementPort;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.test.web.reactive.server.WebTestClient;

/**
 * 게이트웨이는 공개 포트가 밖으로 열려 있으므로 actuator 는 관리 포트에서만 열려야 한다
 */
@DisplayName("[통합] api-gateway 메트릭 엔드포인트 (관리 포트 분리) 테스트")
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = {
    "eureka.client.enabled=false",
    "management.server.port=0"
})
@AutoConfigureObservability
class GatewayActuatorIntegrationTest {

    @LocalServerPort
    private int port;

    @LocalManagementPort
    private int managementPort;

    @Test
    @DisplayName("success case : 관리 포트에서는 health / prometheus 를 볼 수 있다")
    void 관리_포트에서는_health_prometheus_를_볼_수_있다() {
        // given
        WebTestClient management = WebTestClient.bindToServer().baseUrl("http://localhost:" + managementPort).build();

        // when & then
        management.get().uri("/actuator/health").exchange().expectStatus().isOk();
        management.get().uri("/actuator/prometheus").exchange()
            .expectStatus().isOk()
            .expectBody(String.class).value(body -> org.assertj.core.api.Assertions.assertThat(body)
                .contains("application=\"api-gateway\""));
    }

    @Test
    @DisplayName("fail case : 공개 포트의 /actuator 는 열리지 않는다")
    void 공개_포트의_actuator_는_열리지_않는다() {
        // given
        WebTestClient publicPort = WebTestClient.bindToServer().baseUrl("http://localhost:" + port).build();

        // when & then
        publicPort.get().uri("/actuator/prometheus").exchange().expectStatus().isNotFound();
        publicPort.get().uri("/actuator/health").exchange().expectStatus().isNotFound();
    }
}
