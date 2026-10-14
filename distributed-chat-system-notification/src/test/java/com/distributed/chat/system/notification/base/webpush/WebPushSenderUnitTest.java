package com.distributed.chat.system.notification.base.webpush;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.distributed.chat.system.mysql.entity.WebPushSubscription;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Base64;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@DisplayName("[단위] WebPushSender 테스트")
@ExtendWith(MockitoExtension.class)
class WebPushSenderUnitTest {

    /**
     * (FCM 의 예전 /fcm/send 주소는 라이브러리가 웹 푸시 주소 /wp 로 바꾸므로 여기서는 Mozilla 주소를 쓴다)
     */
    private static final String ENDPOINT = "https://updates.push.services.mozilla.com/wpush/v2/abc";

    @Mock
    private HttpClient httpClient;

    @Mock
    private HttpResponse<Void> response;

    private VapidKeys vapidKeys;
    private WebPushSender webPushSender;
    private WebPushSubscription subscription;

    @BeforeEach
    void setup() throws Exception {
        String[] keys = VapidKeyLoader.generate();
        vapidKeys = new VapidKeys(keys[0], keys[1], "mailto:ops@example.com");
        webPushSender = new WebPushSender(new WebPushRequestFactory(vapidKeys), httpClient, 3600, Duration.ofSeconds(5));
        // 브라우저 구독 키 (p256dh = 다른 P-256 공개키, auth = 16바이트)
        subscription = WebPushSubscription.builder().userId(2L).endpoint(ENDPOINT).p256dh(VapidKeyLoader.generate()[0])
            .auth(Base64.getUrlEncoder().withoutPadding().encodeToString("0123456789abcdef".getBytes(StandardCharsets.UTF_8))).build();
    }

    @Test
    @DisplayName("success case : endpoint 로 TTL / Topic / aes128gcm / VAPID 서명 헤더를 붙여 POST 하고 응답 코드를 돌려준다")
    void endpoint_로_헤더를_붙여_POST_하고_응답_코드를_돌려준다() {
        // arrange
        ArgumentCaptor<HttpRequest> request = ArgumentCaptor.forClass(HttpRequest.class);
        when(response.statusCode()).thenReturn(201);
        when(httpClient.<Void>sendAsync(request.capture(), any())).thenReturn(CompletableFuture.completedFuture(response));

        // act
        Integer status = webPushSender.send(subscription, "{\"title\":\"#general\"}".getBytes(StandardCharsets.UTF_8), "channel-7").join();

        // assert
        assertThat(status).isEqualTo(201);
        HttpRequest sent = request.getValue();
        assertThat(sent.uri()).isEqualTo(URI.create(ENDPOINT));
        assertThat(sent.method()).isEqualTo("POST");
        assertThat(sent.timeout()).contains(Duration.ofSeconds(5));
        assertThat(sent.headers().firstValue("TTL")).contains("3600");
        assertThat(sent.headers().firstValue("Topic")).contains("channel-7");
        assertThat(sent.headers().firstValue("Content-Encoding")).contains("aes128gcm");
        assertThat(sent.headers().firstValue("Authorization")).get().asString()
            .startsWith("vapid t=").endsWith(", k=" + vapidKeys.publicKey());
        // 암호문 = 헤더(86바이트) + 본문 + 패딩 구분자 + 태그(16) 이상
        assertThat(sent.bodyPublisher().orElseThrow().contentLength()).isGreaterThan(86 + 16);
    }

    @Test
    @DisplayName("fail case : 구독 키가 망가져 암호화할 수 없으면 보내지 않고 실패한 future 를 돌려준다 (던지지 않는다)")
    void 구독_키가_망가져_암호화할_수_없으면_보내지_않고_실패한_future_를_돌려준다() {
        // arrange
        WebPushSubscription broken = WebPushSubscription.builder().userId(2L).endpoint(ENDPOINT).p256dh("AAAA").auth("AAAA").build();

        // act
        CompletableFuture<Integer> result = webPushSender.send(broken, new byte[]{1}, "channel-7");

        // assert
        assertThat(result).isCompletedExceptionally();
        verify(httpClient, never()).sendAsync(any(), any());
    }

    @Test
    @DisplayName("fail case : 연결 실패 / 타임아웃은 실패한 future 로 돌려준다")
    void 연결_실패_타임아웃은_실패한_future_로_돌려준다() {
        // arrange
        when(httpClient.<Void>sendAsync(any(), any()))
            .thenReturn(CompletableFuture.failedFuture(new java.net.http.HttpTimeoutException("timeout")));

        // act
        Throwable thrown = catchThrowable(() -> webPushSender.send(subscription, new byte[]{1}, "channel-7").join());

        // assert
        assertThat(thrown).isInstanceOf(CompletionException.class).hasCauseInstanceOf(java.net.http.HttpTimeoutException.class);
    }
}
