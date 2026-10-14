package com.distributed.chat.system.connection.status.base.security;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.authentication.InsufficientAuthenticationException;

@DisplayName("[단위] LoginRequiredEntryPoint 테스트")
class LoginRequiredEntryPointUnitTest {

    @Test
    @DisplayName("success case : 로그인하지 않은 요청에는 client-api 와 같은 모양의 401 / 1003 을 돌려준다")
    void 로그인하지_않은_요청에는_client_api_와_같은_모양의_401_1003_을_돌려준다() throws Exception {
        // arrange
        ObjectMapper objectMapper = new ObjectMapper();
        LoginRequiredEntryPoint entryPoint = new LoginRequiredEntryPoint(objectMapper);
        MockHttpServletResponse response = new MockHttpServletResponse();

        // act
        entryPoint.commence(new MockHttpServletRequest(), response, new InsufficientAuthenticationException("no session"));

        // assert
        assertThat(response.getStatus()).isEqualTo(401);
        assertThat(response.getContentType()).startsWith("application/json");
        Map<?, ?> body = objectMapper.readValue(response.getContentAsString(), Map.class);
        assertThat(body).isEqualTo(Map.of(
            "code", 1003,
            "msg", "유저 정보가 존재하지 않습니다.",
            "data", Map.of("errorCodeName", "NOT_EXIST_USER_INFO"),
            "success", false));
    }
}
