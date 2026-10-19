package com.distributed.chat.system.client.api.base.webpush;

import java.util.List;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class WebPushConfig {

    @Bean
    public PushEndpointPolicy pushEndpointPolicy(
        @Value("${chat.notification.push.allowed-endpoint-hosts}") List<String> allowedHosts,
        @Value("${chat.notification.push.allow-insecure-endpoint:false}") boolean allowInsecure) {
        return new PushEndpointPolicy(allowedHosts, allowInsecure);
    }
}
