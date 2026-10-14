package com.distributed.chat.system.connection.status.base.security;

import com.distributed.chat.system.common.exception.ErrorCode;
import com.distributed.chat.system.common.exception.ExceptionResponse;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.stereotype.Component;

/**
 * 로그인 세션이 없으면 401 + {code: 1003, msg, data: {errorCodeName}, success: false}
 * (client-api 가 로그인 정보가 없을 때 돌려주는 응답과 같다)
 */
@Component
@RequiredArgsConstructor
public class LoginRequiredEntryPoint implements AuthenticationEntryPoint {

    private final ObjectMapper objectMapper;

    @Override
    public void commence(HttpServletRequest request, HttpServletResponse response, AuthenticationException authException)
        throws IOException {
        ErrorCode errorCode = ErrorCode.NOT_EXIST_USER_INFO;
        response.setStatus(errorCode.getHttpStatus().value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        objectMapper.writeValue(response.getWriter(), ExceptionResponse.toResponseEntity(errorCode).getBody());
    }
}
