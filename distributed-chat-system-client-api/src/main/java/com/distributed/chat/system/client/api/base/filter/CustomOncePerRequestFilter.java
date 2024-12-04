package com.distributed.chat.system.client.api.base.filter;

import com.distributed.chat.system.mysql.entity.User;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;

@Slf4j
public class CustomOncePerRequestFilter extends OncePerRequestFilter {

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain) throws ServletException, IOException {
        log.info("CustomOncePerRequestFilter IN");
        
        HttpSession session = request.getSession(false);

        if (session != null) {
            log.info("session: {}", session);
            User user = (User) session.getAttribute("user");
            SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(user.getAccount(), "", List.of()));
        }

        filterChain.doFilter(request, response);
    }
}
