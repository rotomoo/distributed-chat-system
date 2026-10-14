package com.distributed.chat.system.notification.web.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowableOfType;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.distributed.chat.system.common.exception.ApiException;
import com.distributed.chat.system.common.exception.ErrorCode;
import com.distributed.chat.system.common.response.ResponseData;
import com.distributed.chat.system.mysql.repository.WebPushSubscriptionRepository;
import com.distributed.chat.system.notification.base.webpush.PushEndpointPolicy;
import com.distributed.chat.system.notification.base.webpush.VapidKeys;
import com.distributed.chat.system.notification.web.dto.SubscribeRequestDto;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@DisplayName("[단위] SubscriptionService 테스트")
@ExtendWith(MockitoExtension.class)
class SubscriptionServiceUnitTest {

    private static final Long LOGIN_ID = 3L;

    @Mock
    private PushEndpointPolicy pushEndpointPolicy;

    @Mock
    private WebPushSubscriptionRepository webPushSubscriptionRepository;

    private SubscriptionService subscriptionService;

    @BeforeEach
    void setup() {
        subscriptionService = new SubscriptionService(new VapidKeys("BPublic", "private", "mailto:ops@example.com"),
            pushEndpointPolicy, webPushSubscriptionRepository);
    }

    @Test
    @DisplayName("success case : VAPID 공개키만 돌려준다 (개인키는 내보내지 않음)")
    void VAPID_공개키만_돌려준다() {
        // act
        ResponseData responseData = subscriptionService.getVapidPublicKey();

        // assert
        assertThat(responseData.getData()).isEqualTo(Map.of("publicKey", "BPublic"));
    }

    @Test
    @DisplayName("success case : 올바른 구독은 로그인 사용자 것으로 endpoint 기준 upsert 한다 (앞뒤 공백 제거)")
    void 올바른_구독은_로그인_사용자_것으로_upsert_한다() {
        // arrange
        when(pushEndpointPolicy.isValid("https://fcm.googleapis.com/x", "p", "a")).thenReturn(true);

        // act
        subscriptionService.subscribe(LOGIN_ID, new SubscribeRequestDto(" https://fcm.googleapis.com/x ", new SubscribeRequestDto.Keys("p ", " a")));

        // assert
        verify(webPushSubscriptionRepository).upsert(LOGIN_ID, "https://fcm.googleapis.com/x", "p", "a");
    }

    @Test
    @DisplayName("fail case : 허용하지 않은 endpoint 나 잘못된 키는 5001 로 거절하고 저장하지 않는다")
    void 허용하지_않은_endpoint_나_잘못된_키는_5001_로_거절한다() {
        // arrange
        when(pushEndpointPolicy.isValid(anyString(), anyString(), anyString())).thenReturn(false);

        // act
        ApiException exception = catchThrowableOfType(() -> subscriptionService.subscribe(LOGIN_ID,
            new SubscribeRequestDto("https://evil.example.com", new SubscribeRequestDto.Keys("p", "a"))), ApiException.class);

        // assert
        assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.INVALID_PUSH_SUBSCRIPTION);
        verify(webPushSubscriptionRepository, never()).upsert(anyLong(), any(), any(), any());
    }

    @Test
    @DisplayName("success case : 구독 해제는 내 구독만 지운다")
    void 구독_해제는_내_구독만_지운다() {
        // act
        subscriptionService.unsubscribe(LOGIN_ID, " https://fcm.googleapis.com/x ");

        // assert
        verify(webPushSubscriptionRepository).deleteByEndpointAndUserId("https://fcm.googleapis.com/x", LOGIN_ID);
    }
}
