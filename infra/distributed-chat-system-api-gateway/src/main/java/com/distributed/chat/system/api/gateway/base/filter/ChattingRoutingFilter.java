package com.distributed.chat.system.api.gateway.base.filter;

import com.distributed.chat.system.common.exception.ApiException;
import com.distributed.chat.system.common.exception.ErrorCode;
import java.net.URI;
import java.util.Optional;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cloud.gateway.filter.GatewayFilter;
import org.springframework.cloud.gateway.filter.factory.AbstractGatewayFilterFactory;
import org.springframework.cloud.gateway.support.ServerWebExchangeUtils;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.http.HttpCookie;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

@Component
@Slf4j
public class ChattingRoutingFilter extends AbstractGatewayFilterFactory<ChattingRoutingFilter.Config> {

    private final RedisTemplate redisTemplate;

    public ChattingRoutingFilter(RedisTemplate redisTemplate) {
        super(Config.class);
        this.redisTemplate = redisTemplate;
    }

    @Override
    public GatewayFilter apply(Config config) {
        // Custom Pre Filter
        return (exchange, chain) -> {
            ServerHttpRequest request = exchange.getRequest();
            ServerHttpResponse response = exchange.getResponse();

            log.info("Custom PRE filter : request id -> {}", request.getId());
            log.info("PRE request URL: {}",
                (Object) exchange.getAttribute(ServerWebExchangeUtils.GATEWAY_REQUEST_URL_ATTR));
            log.info("PRE Host Header: {}",
                (Object) exchange.getAttribute(ServerWebExchangeUtils.PRESERVE_HOST_HEADER_ATTRIBUTE));

            String userId = Optional.ofNullable(request.getCookies().getFirst("userId"))
                .map(HttpCookie::getValue)
                .orElseThrow(() -> new ApiException(ErrorCode.NOT_EXIST_USER_INFO));

            String chatServerUri = (String) Optional.ofNullable(
                redisTemplate.opsForValue().get(userId)).orElse("lb:ws://CHATTING");

            log.info("userId : {}, chatServerUri : {}", userId, chatServerUri);

            if (!chatServerUri.equals("lb:ws://CHATTING")) {
                exchange.getAttributes().put(ServerWebExchangeUtils.GATEWAY_REQUEST_URL_ATTR, URI.create(chatServerUri));
            }

            // Custom Post Filter
            return chain.filter(exchange).then(Mono.fromRunnable(() -> {
                log.info("Custom POST filter : response code -> {}", response.getStatusCode());
                log.info("POST request URL: {}",
                    (Object) exchange.getAttribute(ServerWebExchangeUtils.GATEWAY_REQUEST_URL_ATTR));
                log.info("POST Host Header: {}",
                    (Object) exchange.getAttribute(ServerWebExchangeUtils.PRESERVE_HOST_HEADER_ATTRIBUTE));
            }));
        };
    }


    public static class Config {

    }
}