package com.distributed.chat.system.common.redis;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.cloud.client.DefaultServiceInstance;
import org.springframework.cloud.client.discovery.DiscoveryClient;

@DisplayName("[단위] ChatServers 테스트")
class ChatServersUnitTest {

    @Test
    @DisplayName("success case : Eureka 에 있는 chatting 인스턴스를 ip:port 로 돌려준다")
    void Eureka_에_있는_chatting_인스턴스를_ip_port_로_돌려준다() {
        // arrange
        DiscoveryClient discoveryClient = mock(DiscoveryClient.class);
        when(discoveryClient.getInstances("chatting")).thenReturn(List.of(
            new DefaultServiceInstance("a", "chatting", "10.0.0.1", 1111, false),
            new DefaultServiceInstance("b", "chatting", "10.0.0.2", 2222, false)));

        // act & assert
        assertThat(ChatServers.alive(discoveryClient)).containsExactlyInAnyOrder("10.0.0.1:1111", "10.0.0.2:2222");
    }
}
