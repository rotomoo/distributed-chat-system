package com.distributed.chat.system.client.api.base.config;

import com.distributed.chat.system.client.api.base.filter.CustomOncePerRequestFilter;
import com.distributed.chat.system.client.api.base.security.CustomAuthenticationSuccessHandler;
import com.distributed.chat.system.client.api.base.security.CustomUserDetailsService;
import java.util.List;
import lombok.RequiredArgsConstructor;
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
import org.springframework.security.web.authentication.SimpleUrlAuthenticationFailureHandler;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

@Configuration
@EnableWebSecurity
@RequiredArgsConstructor
public class SecurityConfig {

    private final CustomUserDetailsService customUserDetailsService;

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
                .requestMatchers(privatePaths).authenticated()
                .requestMatchers(publicPaths).permitAll()
                .anyRequest().authenticated()
            )
            .formLogin((config) ->
                config.loginProcessingUrl("/login")
                    .usernameParameter("account")
                    .successHandler(new CustomAuthenticationSuccessHandler())
                    .failureHandler(new SimpleUrlAuthenticationFailureHandler())
                    .permitAll()
            )
            .sessionManagement((config) ->
                config.sessionCreationPolicy(SessionCreationPolicy.STATELESS)
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
