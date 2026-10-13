package com.distributed.chat.system.common.interceptor;

import static org.assertj.core.api.Assertions.assertThat;

import com.distributed.chat.system.common.filter.CustomHttpServletRequestWrapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

@DisplayName("[단위] LoggingInterceptor 테스트")
class LoggingInterceptorUnitTest {

    private static final byte[] BODY = "{\"channelName\":\"general\"}".getBytes(StandardCharsets.UTF_8);

    private final LoggingInterceptor loggingInterceptor = new LoggingInterceptor(new ObjectMapper());

    @Test
    @DisplayName("success case : 필터가 복사하지 않은 요청은 원래 본문 스트림을 읽지 않는다 (컨트롤러가 그대로 읽을 수 있다)")
    void 필터가_복사하지_않은_요청은_원래_본문_스트림을_읽지_않는다() throws Exception {
        // arrange
        MockHttpServletRequest request = jsonRequest();

        // act
        boolean proceed = loggingInterceptor.preHandle(request, new MockHttpServletResponse(), new Object());

        // assert
        assertThat(proceed).isTrue();
        assertThat(request.getInputStream().readAllBytes()).isEqualTo(BODY);
    }

    @Test
    @DisplayName("success case : 필터가 복사한 요청은 복사본으로 로그를 남기고 본문은 그대로 다시 읽을 수 있다")
    void 필터가_복사한_요청은_복사본으로_로그를_남기고_본문은_그대로_다시_읽을_수_있다() throws Exception {
        // arrange
        CustomHttpServletRequestWrapper cached = new CustomHttpServletRequestWrapper(jsonRequest());

        // act
        boolean proceed = loggingInterceptor.preHandle(cached, new MockHttpServletResponse(), new Object());

        // assert
        assertThat(proceed).isTrue();
        assertThat(cached.getInputStream().readAllBytes()).isEqualTo(BODY);
    }

    private static MockHttpServletRequest jsonRequest() {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/v1/private/api/file/upload-json");
        request.setContentType("application/json");
        request.setContent(BODY);
        return request;
    }
}
