package com.distributed.chat.system.client.api.web.service;

import com.distributed.chat.system.client.api.web.dto.authentication.SignupRequestDto;
import com.distributed.chat.system.common.exception.ApiException;
import com.distributed.chat.system.common.exception.ErrorCode;
import com.distributed.chat.system.common.response.ResponseData;
import com.distributed.chat.system.mysql.entity.User;
import com.distributed.chat.system.mysql.repository.UserRepository;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Slf4j
public class AuthenticationService {

    private final UserRepository userRepository;

    private final PasswordEncoder passwordEncoder;

    /**
     * 회원가입
     *
     * @param signupRequestDto
     * @return
     */
    @Transactional
    public ResponseData signup(SignupRequestDto signupRequestDto) {
        if (userRepository.existsByAccount(signupRequestDto.getAccount())) {
            throw new ApiException(ErrorCode.ALREADY_USING_ACCOUNT);
        }

        User user = saveUser(signupRequestDto);

        return ResponseData.success("회원가입", Map.of("user", user));
    }

    public User saveUser(SignupRequestDto signupRequestDto) {
        return userRepository.save(createUserEntity(signupRequestDto));
    }

    public User createUserEntity(SignupRequestDto signupRequestDto) {
        return User.builder()
            .account(signupRequestDto.getAccount())
            .userName(signupRequestDto.getAccount())
            .password(passwordEncoder.encode(signupRequestDto.getPassword()))
            .build();
    }
}
