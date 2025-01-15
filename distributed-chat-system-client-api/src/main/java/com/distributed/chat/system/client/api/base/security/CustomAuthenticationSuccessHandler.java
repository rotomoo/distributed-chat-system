package com.distributed.chat.system.client.api.base.security;

import com.distributed.chat.system.client.api.web.controller.feign.ServiceDiscoveryClient;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import java.io.IOException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.Authentication;
import org.springframework.security.web.authentication.AuthenticationSuccessHandler;

@Slf4j
@RequiredArgsConstructor
public class CustomAuthenticationSuccessHandler implements AuthenticationSuccessHandler {

    private final ServiceDiscoveryClient serviceDiscoveryClient;

    @Override
    public void onAuthenticationSuccess(HttpServletRequest request, HttpServletResponse response,
        Authentication authentication) throws IOException, ServletException {

        log.info("login success handler in");

        HttpSession session = request.getSession(false);
        session.setAttribute("authentication", authentication);

        String chatServiceUrl = serviceDiscoveryClient.getChatServiceUrl();
        log.info("login success handler chatServerUrl: {}, authentication : {}", chatServiceUrl,
            authentication);

        session.setAttribute("chatServiceUrl", chatServiceUrl);
    }
}
