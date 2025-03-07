package com.distributed.chat.system.client.api.base.security;

import jakarta.servlet.ServletException;
import jakarta.servlet.http.Cookie;
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

    private final int loginSuccessSessionTimeout;

    @Override
    public void onAuthenticationSuccess(HttpServletRequest request, HttpServletResponse response,
        Authentication authentication) throws IOException, ServletException {

        log.info("login success handler in");

        HttpSession session = request.getSession(false);
        session.setMaxInactiveInterval(loginSuccessSessionTimeout);
        session.setAttribute("authentication", authentication);

        String userId = authentication.getName();
        session.setAttribute("userId", userId);

        // 로그인시 userId 쿠키 생성
        Cookie cookie = new Cookie("userId", authentication.getName());
        cookie.setPath("/");
        cookie.setHttpOnly(true);
        cookie.setSecure(false);
        cookie.setMaxAge(loginSuccessSessionTimeout);
        response.addCookie(cookie);
    }
}
