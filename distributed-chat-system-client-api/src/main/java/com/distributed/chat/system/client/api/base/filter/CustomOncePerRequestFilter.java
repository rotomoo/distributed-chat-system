package com.distributed.chat.system.client.api.base.filter;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import java.io.IOException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

@Slf4j
public class CustomOncePerRequestFilter extends OncePerRequestFilter {

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
        FilterChain filterChain) throws ServletException, IOException {
        log.info("CustomOncePerRequestFilter - doFilterInternal method in");

        HttpSession session = request.getSession(false);

        if (session != null) {
            Authentication authentication = (Authentication) session.getAttribute("authentication");
            SecurityContextHolder.getContext().setAuthentication(authentication);

            String chatServiceUrl = (String) session.getAttribute("chatServiceUrl");

            if (request.getRequestURI().equals("/") && chatServiceUrl != null) {
                response.sendRedirect(chatServiceUrl);
                return;
            }
        }

        filterChain.doFilter(request, response);
    }
}
