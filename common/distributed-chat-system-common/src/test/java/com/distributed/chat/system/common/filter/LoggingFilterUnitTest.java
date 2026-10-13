package com.distributed.chat.system.common.filter;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;

@DisplayName("[단위] LoggingFilter 테스트")
class LoggingFilterUnitTest {

    private final LoggingFilter loggingFilter = new LoggingFilter(List.of("/v1/private/api/file/**", ""));

    @Test
    @DisplayName("success case : 파일 업로드(multipart)와 지정한 경로는 본문을 복사하지 않는다")
    void 파일_업로드와_지정한_경로는_본문을_복사하지_않는다() {
        // arrange
        MockHttpServletRequest upload = new MockHttpServletRequest("POST", "/v1/private/api/other");
        upload.setContentType("multipart/form-data; boundary=abc");
        MockHttpServletRequest download = new MockHttpServletRequest("GET", "/v1/private/api/file/6acc66675f99715e286f0f6e");

        // act & assert
        assertThat(loggingFilter.shouldNotFilter(upload)).isTrue();
        assertThat(loggingFilter.shouldNotFilter(download)).isTrue();
    }

    @Test
    @DisplayName("success case : 일반 JSON 요청은 지금처럼 본문을 복사한다")
    void 일반_JSON_요청은_지금처럼_본문을_복사한다() {
        // arrange
        MockHttpServletRequest json = new MockHttpServletRequest("POST", "/v1/private/api/team/team");
        json.setContentType("application/json");

        // act & assert
        assertThat(loggingFilter.shouldNotFilter(json)).isFalse();
    }
}
