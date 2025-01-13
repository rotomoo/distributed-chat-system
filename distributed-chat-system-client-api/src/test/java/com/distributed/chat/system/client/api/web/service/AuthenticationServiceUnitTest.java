package com.distributed.chat.system.client.api.web.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowableOfType;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.distributed.chat.system.client.api.web.dto.authentication.SignupRequestDto;
import com.distributed.chat.system.common.exception.ApiException;
import com.distributed.chat.system.common.exception.ErrorCode;
import com.distributed.chat.system.common.response.ResponseData;
import com.distributed.chat.system.mysql.entity.User;
import com.distributed.chat.system.mysql.repository.UserRepository;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;

@DisplayName("[단위] AuthenticationService 테스트")
@ExtendWith(MockitoExtension.class)
class AuthenticationServiceUnitTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private PasswordEncoder passwordEncoder;

    @InjectMocks
    private AuthenticationService authenticationService;

    private SignupRequestDto testDto;

    @BeforeEach
    void setup() {
        testDto = new SignupRequestDto("testAccount", "password");
    }

    @Test
    @DisplayName("true case: 회원가입 성공")
    void signupTrue() {
        // arrange
        when(userRepository.existsByAccount(testDto.getAccount())).thenReturn(false);
        when(passwordEncoder.encode(testDto.getPassword())).thenReturn("encodedPassword");
        when(userRepository.save(any(User.class)))
            .thenReturn(new User(1L, "testName", "testAccount", "encodedPassword", false));

        // act
        ResponseData<Map<String, User>> response = authenticationService.signup(testDto);

        // assert
        assertThat(response.isSuccess()).isTrue();
        assertThat(response.getCode()).isEqualTo(0);
        assertThat(response.getMsg()).isEqualTo("회원가입");

        User user = response.getData().get("user");
        assertThat(user.getAccount()).isEqualTo("testAccount");
        assertThat(user.getPassword()).isEqualTo("encodedPassword");
        verify(userRepository, times(1)).save(any(User.class));
    }

    @Test
    @DisplayName("false case: 중복 계정 회원가입 실패")
    void signupFalse() {
        // arrange
        when(userRepository.existsByAccount("testAccount")).thenReturn(true);

        // act
        ApiException exception = catchThrowableOfType(() -> authenticationService.signup(testDto),
            ApiException.class);

        // assert
        assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.ALREADY_USING_ACCOUNT);
        assertThat(exception.getErrorCode().getCustomCode()).isEqualTo(1001);
        assertThat(exception.getErrorCode().getHttpStatus()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(exception.getErrorCode().getMsg()).isEqualTo("이미 사용중인 계정입니다.");
    }

    @Test
    @DisplayName("true case: 유저 엔티티 저장")
    void saveUser() {
        // arrange
        when(userRepository.save(any(User.class)))
            .thenReturn(new User(1L, "testName", "testAccount", "encodedPassword", false));

        // act
        User user = authenticationService.saveUser(testDto);

        // assert
        assertThat(user.getAccount()).isEqualTo("testAccount");
    }

    @Test
    @DisplayName("true case: 유저 엔티티 생성")
    void createUserEntity() {
        // arrange
        when(passwordEncoder.encode(testDto.getPassword())).thenReturn("encodedPassword");

        // act
        User user = authenticationService.createUserEntity(testDto);

        // assert
        assertThat(user.getAccount()).isEqualTo("testAccount");
        assertThat(user.getPassword()).isEqualTo("encodedPassword");
    }
}