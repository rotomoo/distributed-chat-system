package com.distributed.chat.system.connection.status.base.config;

import com.distributed.chat.system.connection.status.base.security.LoginRequiredEntryPoint;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configuration.WebSecurityCustomizer;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;

/**
 * 로그인은 client-api 가 한다. 이 서버는 같은 SESSION 쿠키로 Redis 에 저장된 로그인 세션(Spring Session)을 읽기만 한다
 * <ul>
 *   <li>세션을 만들지 않는다 (NEVER, 요청 캐시 끔) : 로그인 안 한 요청마다 빈 세션이 Redis 에 쌓이지 않게</li>
 *   <li>로그인 안 했으면 401 + 1003 (client-api 의 NOT_EXIST_USER_INFO 응답과 같은 모양)</li>
 * </ul>
 */
@Configuration
@EnableWebSecurity
@RequiredArgsConstructor
public class SecurityConfig {

    private final LoginRequiredEntryPoint loginRequiredEntryPoint;

    @Bean
    public WebSecurityCustomizer webSecurityCustomizer() {
        return (web) -> web.ignoring().requestMatchers(
            "/favicon.ico",
            "/actuator",
            "/actuator/**",
            "/error",
            "/swagger-ui/**",
            "/v3/**",
            "/*.html",
            "/*.js",
            "/*.css"
        );
    }

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        return http
            .csrf(AbstractHttpConfigurer::disable)
            .authorizeHttpRequests((requests) -> requests
                .anyRequest().authenticated()
            )
            .formLogin(AbstractHttpConfigurer::disable)
            .httpBasic(AbstractHttpConfigurer::disable)
            .logout(AbstractHttpConfigurer::disable)
            .requestCache(AbstractHttpConfigurer::disable)
            .sessionManagement((config) ->
                config.sessionCreationPolicy(SessionCreationPolicy.NEVER)
            )
            .exceptionHandling((config) ->
                config.authenticationEntryPoint(loginRequiredEntryPoint)
            )
            .build();
    }
}
