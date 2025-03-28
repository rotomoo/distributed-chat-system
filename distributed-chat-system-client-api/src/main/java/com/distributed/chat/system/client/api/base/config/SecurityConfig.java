package com.distributed.chat.system.client.api.base.config;

import com.distributed.chat.system.client.api.base.filter.CustomOncePerRequestFilter;
import com.distributed.chat.system.client.api.base.security.CustomAuthenticationSuccessHandler;
import com.distributed.chat.system.client.api.base.security.CustomSimpleUrlAuthenticationFailureHandler;
import com.distributed.chat.system.client.api.base.security.CustomUserDetailsService;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configuration.WebSecurityCustomizer;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

@Configuration
@EnableWebSecurity
@RequiredArgsConstructor
public class SecurityConfig {

    private final CustomUserDetailsService customUserDetailsService;

    @Value("${front-end.url}")
    private String frontEndUrl;

    @Value("${login-success.session.timeout}")
    private int loginSuccessSessionTimeout;

    private final String[] publicPaths = List.of(
        "/v1/public/**",
        "/"
    ).toArray(new String[0]);

    private final String[] privatePaths = List.of(
        "/v1/private/**"
    ).toArray(new String[0]);

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
                // TODO 스웨거 테스트로 permitAll 처리함. 추후 스웨거에서 로그인 할 수 있게 설정 및 authenticated() 처리
                .requestMatchers(privatePaths).permitAll()
                .requestMatchers(publicPaths).permitAll()
                .anyRequest().authenticated()
            )
            .formLogin((config) ->
                config
                    .loginPage(frontEndUrl)
                    .loginProcessingUrl("/v1/public/api/auth/login")
                    .usernameParameter("account")
                    .successHandler(
                        new CustomAuthenticationSuccessHandler(loginSuccessSessionTimeout))
                    .failureHandler(new CustomSimpleUrlAuthenticationFailureHandler())
                    .permitAll()
            )
            .logout((config) ->
                config
                    .logoutUrl("/v1/private/api/user/logout")
                    .logoutSuccessUrl(frontEndUrl)
                    .invalidateHttpSession(true)
            )
            .sessionManagement((config) ->
                config.sessionCreationPolicy(SessionCreationPolicy.IF_REQUIRED)
            )
            .userDetailsService(customUserDetailsService)
            .addFilterBefore(new CustomOncePerRequestFilter(),
                UsernamePasswordAuthenticationFilter.class)
            .build();
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }
}
