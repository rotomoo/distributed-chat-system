package com.distributed.chat.system.common.config;

import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.platform.commons.util.StringUtils;
import org.springframework.boot.test.context.SpringBootTest;

@DisplayName("[통합] jasypt 의존성 테스트")
@SpringBootTest
class JasyptDependencyIntegrationTest {

    @Test
    @DisplayName("true case: test 환경변수 주입 테스트")
    void testJasyptPassword() {

        // given

        // when
        String password = System.getProperty("jasypt.encryptor.password");

        // then
        assertTrue(StringUtils.isNotBlank(password));
    }
}
