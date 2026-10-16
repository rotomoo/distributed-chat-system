package com.distributed.chat.system.api.gateway.base.filter;

import com.distributed.chat.system.common.redis.ChatPresenceKeys;
import com.distributed.chat.system.common.redis.ChatPresenceKeys.ChatPresence;
import com.distributed.chat.system.common.redis.ChatServers;
import com.distributed.chat.system.common.exception.ApiException;
import com.distributed.chat.system.common.exception.ErrorCode;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cloud.client.discovery.DiscoveryClient;
import org.springframework.cloud.gateway.filter.GatewayFilter;
import org.springframework.cloud.gateway.filter.factory.AbstractGatewayFilterFactory;
import org.springframework.cloud.gateway.route.Route;
import org.springframework.cloud.gateway.support.ServerWebExchangeUtils;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.http.HttpCookie;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

@Component
@Slf4j
@RequiredArgsConstructor
public class ChattingRoutingFilter extends AbstractGatewayFilterFactory<Object> {

    private final RedisTemplate redisTemplate;
    private final DiscoveryClient discoveryClient;

    @Override
    public GatewayFilter apply(Object object) {
        // Custom Pre Filter
        return (exchange, chain) -> {
            ServerHttpRequest request = exchange.getRequest();
            ServerHttpResponse response = exchange.getResponse();
            Route prevRoute = (Route) exchange.getAttributes().get(ServerWebExchangeUtils.GATEWAY_ROUTE_ATTR);

            log.info("Custom PRE filter : request id -> {}", request.getId());
            log.info("PRE route URL: {}", prevRoute);

            String userId = Optional.ofNullable(request.getCookies().getFirst("userId"))
                .map(HttpCookie::getValue)
                .orElseThrow(() -> new ApiException(ErrorCode.NOT_EXIST_USER_INFO));

            // {userId} = {server, status} (예전 값 "ws://..." 도 읽는다). 키가 없으면(첫 접속 / 끊긴 지 오래 / 서버가 죽고 TTL 이 지남) null
            ChatPresence presence = ChatPresenceKeys.parse(redisTemplate.opsForValue().get(ChatPresenceKeys.userKey(userId)));
            String chatServerUri = presence == null ? null : presence.server();

            log.info("userId : {}, chatServerUri : {}", userId, chatServerUri);

            // 이미 접속한 서버가 살아 있을 때만 그 서버로 (유저 하나 = 서버 하나). 죽은 서버를 가리키면 로드밸런서에 맡긴다
            if (chatServerUri != null && !ChatServers.alive(discoveryClient).contains(presence.chatServer())) {
                log.info("userId : {}, chatServerUri : {} is dead, fall back to load balancer", userId, chatServerUri);
                chatServerUri = null;
            }

            if (chatServerUri != null) {
                Route route = Route.async()
                    .id(prevRoute.getId())
                    .uri(chatServerUri)
                    .order(prevRoute.getOrder())
                    .asyncPredicate(prevRoute.getPredicate())
                    .filters(prevRoute.getFilters())
                    .build();

                exchange.getAttributes().put(ServerWebExchangeUtils.GATEWAY_ROUTE_ATTR, route);
            }

            // Custom Post Filter
            return chain.filter(exchange).then(Mono.fromRunnable(() -> {
                log.info("Custom POST filter : response code -> {}", response.getStatusCode());
                log.info("POST route URL: {}", (Route) exchange.getAttributes().get(ServerWebExchangeUtils.GATEWAY_ROUTE_ATTR));
            }));
        };
    }
}