package com.distributed.chat.system.client.api.web.service;

import com.distributed.chat.system.client.api.web.dto.authentication.SignupRequestDto;
import com.distributed.chat.system.common.exception.ApiException;
import com.distributed.chat.system.common.exception.ErrorCode;
import com.distributed.chat.system.common.response.ResponseData;
import com.distributed.chat.system.common.util.StringUtil;
import com.distributed.chat.system.mysql.entity.User;
import com.distributed.chat.system.mysql.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.config.annotation.authentication.builders.AuthenticationManagerBuilder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;

@Service
@RequiredArgsConstructor
@Slf4j
public class AuthenticationService {

    private final UserRepository userRepository;

    private final PasswordEncoder passwordEncoder;

    private final AuthenticationManagerBuilder authenticationManagerBuilder;


    /**
     * 회원가입
     *
     * @param signupRequestDto
     * @return
     */
    @Transactional
    public ResponseData signup(SignupRequestDto signupRequestDto) {

        if (userRepository.existsByAccount(signupRequestDto.getAccount())) {
            throw new ApiException(ErrorCode.ALREADY_USE_ACCOUNT);
        }

        User user = signupUser(signupRequestDto);

        return ResponseData.success("회원가입", Map.of("userId", user.getId()));
    }

    /**
     * 회원가입 user 저장 후 반환
     *
     * @param signupRequestDto
     * @return
     */
    private User signupUser(SignupRequestDto signupRequestDto) {
        User user = User.builder()
                .account(signupRequestDto.getAccount())
                .userName(signupRequestDto.getAccount())
                .password(passwordEncoder.encode(signupRequestDto.getPassword()))
                .salt(StringUtil.createSalt())
                .deletedYn(false)
                .build();

        userRepository.save(user);

        return user;
    }
}
