package com.distributed.chat.system.chatting.base.interceptor;

import jakarta.servlet.http.HttpServletRequest;
import java.util.Map;
import javax.naming.AuthenticationException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.http.server.ServletServerHttpRequest;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.WebSocketHandler;
import org.springframework.web.socket.server.HandshakeInterceptor;

@Slf4j
@Component
@RequiredArgsConstructor
public class CustomHandShakeInterceptor implements HandshakeInterceptor {

    private final RedisTemplate redisTemplate;

    @Value("${spring.session.redis.namespace}")
    private String sessionNamespace;

    @Override
    public boolean beforeHandshake(ServerHttpRequest request, ServerHttpResponse response,
        WebSocketHandler wsHandler, Map<String, Object> attributes) throws Exception {
        log.info("CustomHandShakeInterceptor beforeHandshake in");

        HttpServletRequest httpServletRequest = ((ServletServerHttpRequest) request).getServletRequest();

        String httpSessionId = httpServletRequest.getSession(false).getId();
//        String channelId = httpServletRequest.getParameter("channelId");

        Map sessionData = redisTemplate.opsForHash().entries(sessionNamespace + ":sessions:" + httpSessionId);
        String userId = (String) sessionData.get("sessionAttr:userId");

        if (httpSessionId != null && userId != null) {
            attributes.put("httpSessionId", httpSessionId);
//            attributes.put("channelId", channelId);
            attributes.put("userId", userId);
            return true;
        }
        throw new AuthenticationException("로그인 해주세요.");
    }

    @Override
    public void afterHandshake(ServerHttpRequest request, ServerHttpResponse response,
        WebSocketHandler wsHandler, Exception exception) {
    }
}
