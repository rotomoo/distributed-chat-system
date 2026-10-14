package com.distributed.chat.system.notification.base.webpush;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Base64;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("[단위] PushEndpointPolicy 테스트")
class PushEndpointPolicyUnitTest {

    private static final List<String> HOSTS = List.of("fcm.googleapis.com", " .push.apple.com ", "updates.push.services.mozilla.com");

    private final PushEndpointPolicy policy = new PushEndpointPolicy(HOSTS, false);

    private final String p256dh = VapidKeyLoader.generate()[0];
    private final String auth = Base64.getUrlEncoder().withoutPadding().encodeToString(new byte[16]);

    @Test
    @DisplayName("success case : 허용한 푸시 서비스의 https endpoint 와 올바른 키는 받는다 (점으로 시작하면 하위 도메인)")
    void 허용한_푸시_서비스의_https_endpoint_와_올바른_키는_받는다() {
        // act & assert
        assertThat(policy.isValid("https://fcm.googleapis.com/fcm/send/abc:APA91", p256dh, auth)).isTrue();
        assertThat(policy.isValid("https://web.push.apple.com/QF2x", p256dh, auth)).isTrue();
        assertThat(policy.isValid("https://updates.push.services.mozilla.com/wpush/v2/gAAA", p256dh, auth)).isTrue();
        // 브라우저에 따라 패딩이 붙어 와도 된다
        assertThat(policy.isValid("https://fcm.googleapis.com/x", p256dh + "=", auth + "==")).isTrue();
    }

    @Test
    @DisplayName("fail case : 허용하지 않은 호스트, http, 내부 주소, 사용자 정보가 붙은 URL 은 받지 않는다 (SSRF)")
    void 허용하지_않은_호스트_http_내부_주소는_받지_않는다() {
        // act & assert
        assertThat(policy.isValid("https://evil.example.com/push", p256dh, auth)).isFalse();
        assertThat(policy.isValid("https://fcm.googleapis.com.evil.com/push", p256dh, auth)).isFalse();
        assertThat(policy.isValid("https://push.apple.com.evil.com/push", p256dh, auth)).isFalse();
        assertThat(policy.isValid("http://fcm.googleapis.com/push", p256dh, auth)).isFalse();
        assertThat(policy.isValid("https://10.0.0.5/internal", p256dh, auth)).isFalse();
        assertThat(policy.isValid("https://user@fcm.googleapis.com/push", p256dh, auth)).isFalse();
        assertThat(policy.isValid("not a url", p256dh, auth)).isFalse();
        assertThat(policy.isValid("https://fcm.googleapis.com/" + "a".repeat(1000), p256dh, auth)).isFalse();
    }

    @Test
    @DisplayName("success case : 테스트용으로 http 를 허용하면 허용 목록 호스트의 http 는 받는다")
    void http_를_허용하면_허용_목록_호스트의_http_는_받는다() {
        // arrange
        PushEndpointPolicy insecure = new PushEndpointPolicy(List.of("127.0.0.1"), true);

        // act & assert
        assertThat(insecure.isValid("http://127.0.0.1:8123/push/1", p256dh, auth)).isTrue();
        assertThat(insecure.isValid("http://10.0.0.1:8123/push/1", p256dh, auth)).isFalse();
    }

    @Test
    @DisplayName("fail case : p256dh 가 65바이트 비압축 공개키가 아니거나 auth 가 16바이트가 아니면 받지 않는다")
    void 키_형식이_틀리면_받지_않는다() {
        // arrange
        String endpoint = "https://fcm.googleapis.com/x";
        String compressed = Base64.getUrlEncoder().withoutPadding().encodeToString(new byte[65]);

        // act & assert
        assertThat(policy.isValid(endpoint, compressed, auth)).isFalse();
        assertThat(policy.isValid(endpoint, "abc", auth)).isFalse();
        assertThat(policy.isValid(endpoint, "!!!", auth)).isFalse();
        assertThat(policy.isValid(endpoint, p256dh, Base64.getUrlEncoder().encodeToString(new byte[15]))).isFalse();
        assertThat(policy.isValid(endpoint, p256dh, "")).isFalse();
        assertThat(policy.isValid(endpoint, null, auth)).isFalse();
    }
}
