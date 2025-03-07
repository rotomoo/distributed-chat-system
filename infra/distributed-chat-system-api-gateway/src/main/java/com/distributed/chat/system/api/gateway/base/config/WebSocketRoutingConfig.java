package com.distributed.chat.system.api.gateway.base.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.web.reactive.socket.client.TomcatWebSocketClient;
import org.springframework.web.reactive.socket.client.WebSocketClient;
import org.springframework.web.reactive.socket.server.RequestUpgradeStrategy;
import org.springframework.web.reactive.socket.server.upgrade.TomcatRequestUpgradeStrategy;

/**
 * 스프링 클라우드 게이트웨이는 기본적으로 네티를 사용함.
 * https://stackoverflow.com/questions/75644784/spring-cloud-route-to-webscoket-service-causes-responsefacade-cannot-be-cast-to
 */
@Configuration
public class WebSocketRoutingConfig {

    @Bean
    @Primary
    WebSocketClient tomcatWebSocketClient() {
        return new TomcatWebSocketClient();
    }

    @Bean
    @Primary
    public RequestUpgradeStrategy requestUpgradeStrategy() {
        return new TomcatRequestUpgradeStrategy();
    }
}
