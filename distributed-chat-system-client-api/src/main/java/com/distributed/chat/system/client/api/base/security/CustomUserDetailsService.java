package com.distributed.chat.system.client.api.base.security;

import com.distributed.chat.system.mysql.entity.User;
import com.distributed.chat.system.mysql.repository.UserRepository;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@Slf4j
public class CustomUserDetailsService implements UserDetailsService {

    private final UserRepository userRepository;

    @Override
    public UserDetails loadUserByUsername(String username) throws UsernameNotFoundException {
        log.info("CustomUserDetailsService - loadUserByUsername method in");

        User user = userRepository.findByAccount(username)
            .orElseThrow(() -> new UsernameNotFoundException("없는 계정입니다."));

        return new org.springframework.security.core.userdetails.User(String.valueOf(user.getId()),
            user.getPassword(), List.of());
    }
}
