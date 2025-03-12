package com.distributed.chat.system.chatting.base.config;

import com.distributed.chat.system.chatting.base.handler.CustomTextWebSocketHandler;
import com.distributed.chat.system.chatting.base.interceptor.CustomHandShakeInterceptor;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.socket.config.annotation.EnableWebSocket;
import org.springframework.web.socket.config.annotation.WebSocketConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistry;

@Configuration
@EnableWebSocket
@RequiredArgsConstructor
public class WebSocketConfiguration implements WebSocketConfigurer {

    private final CustomTextWebSocketHandler customTextWebSocketHandler;

    private final CustomHandShakeInterceptor customHandShakeInterceptor;

    @Override
    public void registerWebSocketHandlers(WebSocketHandlerRegistry registry) {
        registry.addHandler(customTextWebSocketHandler, "/websocket")
            .addInterceptors(customHandShakeInterceptor)
            .setAllowedOrigins("*");
    }
}
