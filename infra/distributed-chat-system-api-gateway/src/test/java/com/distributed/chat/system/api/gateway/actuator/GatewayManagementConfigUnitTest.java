package com.distributed.chat.system.api.gateway.actuator;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.env.YamlPropertySourceLoader;
import org.springframework.core.env.PropertySource;
import org.springframework.core.io.ClassPathResource;

/**
 * 통합 테스트는 관리 포트를 랜덤(0)으로 덮어쓰므로, 실제 설정 파일에 관리 포트가 따로 잡혀 있는지는 여기서 본다
 */
@DisplayName("[단위] api-gateway 관리 포트 설정 테스트")
class GatewayManagementConfigUnitTest {

    @Test
    @DisplayName("success case : actuator 는 공개 포트와 다른 관리 포트(기본 8081)에서 health, prometheus 만 연다")
    void actuator_는_공개_포트와_다른_관리_포트에서_health_prometheus_만_연다() throws Exception {
        // arrange
        List<PropertySource<?>> sources = new YamlPropertySourceLoader().load("gateway", new ClassPathResource("application.yml"));
        PropertySource<?> application = sources.get(0);

        // act
        Object managementPort = application.getProperty("management.server.port");
        Object serverPort = application.getProperty("server.port");
        Object exposure = application.getProperty("management.endpoints.web.exposure.include");

        // assert
        assertThat(managementPort).hasToString("${MANAGEMENT_PORT:8081}");
        assertThat(serverPort).hasToString("8080");
        assertThat(exposure).hasToString("health,prometheus");
    }
}
