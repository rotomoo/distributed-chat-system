package com.distributed.chat.system.api.gateway.base.filter;

import org.springframework.cloud.gateway.filter.GatewayFilter;
import org.springframework.cloud.gateway.filter.factory.AbstractGatewayFilterFactory;
import org.springframework.cloud.gateway.support.ServerWebExchangeUtils;
import org.springframework.stereotype.Component;

@Component
public class ChattingRoutingFilter extends
    AbstractGatewayFilterFactory<ChattingRoutingFilter.Config> {

    public ChattingRoutingFilter() {
        super(Config.class);
    }

    @Override
    public GatewayFilter apply(Config config) {
        // Custom Pre Filter
        return (exchange, chain) -> exchange.getSession()
            .flatMap(session -> {
                String chatServerUri = session.getAttributeOrDefault("chatServerUri",
                    "lb://CHATTING");
                exchange.getAttributes()
                    .put(ServerWebExchangeUtils.GATEWAY_REQUEST_URL_ATTR, chatServerUri);

                return chain.filter(exchange);
            });
    }

    public static class Config {

    }
}
