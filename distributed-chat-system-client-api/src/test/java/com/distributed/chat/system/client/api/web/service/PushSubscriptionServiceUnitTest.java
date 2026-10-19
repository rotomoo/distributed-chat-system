package com.distributed.chat.system.client.api.web.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowableOfType;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.distributed.chat.system.client.api.base.webpush.PushEndpointPolicy;
import com.distributed.chat.system.client.api.web.dto.notification.SubscribeRequestDto;
import com.distributed.chat.system.common.exception.ApiException;
import com.distributed.chat.system.common.exception.ErrorCode;
import com.distributed.chat.system.common.response.ResponseData;
import com.distributed.chat.system.mysql.entity.VapidKey;
import com.distributed.chat.system.mysql.repository.VapidKeyRepository;
import com.distributed.chat.system.mysql.repository.WebPushSubscriptionRepository;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

@DisplayName("[단위] PushSubscriptionService 테스트")
@ExtendWith(MockitoExtension.class)
class PushSubscriptionServiceUnitTest {

    private static final Long LOGIN_ID = 3L;

    @Mock
    private PushEndpointPolicy pushEndpointPolicy;

    @Mock
    private WebPushSubscriptionRepository webPushSubscriptionRepository;

    @Mock
    private VapidKeyRepository vapidKeyRepository;

    private PushSubscriptionService pushSubscriptionService;

    @BeforeEach
    void setup() {
        pushSubscriptionService = service("");
        SecurityContextHolder.getContext().setAuthentication(
            new UsernamePasswordAuthenticationToken(String.valueOf(LOGIN_ID), null, List.of()));
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    @DisplayName("success case : 환경변수 공개키가 있으면 그 값을 돌려주고 MySQL 은 보지 않는다")
    void 환경변수_공개키가_있으면_그_값을_돌려주고_MySQL_은_보지_않는다() {
        // arrange
        PushSubscriptionService configured = service(" BConfigured ");

        // act
        ResponseData responseData = configured.getVapidPublicKey();

        // assert
        assertThat(responseData.getData()).isEqualTo(Map.of("publicKey", "BConfigured"));
        verifyNoInteractions(vapidKeyRepository);
    }

    @Test
    @DisplayName("success case : 환경변수가 없으면 notification-consumer 가 저장한 공개키를 읽고, 두 번째부터는 다시 읽지 않는다")
    void 환경변수가_없으면_저장된_공개키를_읽고_두_번째부터는_다시_읽지_않는다() {
        // arrange
        when(vapidKeyRepository.findPublicKeyById(VapidKey.SINGLE_ID)).thenReturn(Optional.of("BStored"));

        // act
        ResponseData first = pushSubscriptionService.getVapidPublicKey();
        ResponseData second = pushSubscriptionService.getVapidPublicKey();

        // assert
        assertThat(first.getData()).isEqualTo(Map.of("publicKey", "BStored"));
        assertThat(second.getData()).isEqualTo(Map.of("publicKey", "BStored"));
        verify(vapidKeyRepository, times(1)).findPublicKeyById(anyInt());
    }

    @Test
    @DisplayName("fail case : 키가 아직 없으면 (notification-consumer 가 한 번도 안 떴음) 5002 이고, 생기면 그때부터 돌려준다")
    void 키가_아직_없으면_5002_이고_생기면_그때부터_돌려준다() {
        // arrange
        when(vapidKeyRepository.findPublicKeyById(VapidKey.SINGLE_ID)).thenReturn(Optional.empty(), Optional.of("BLater"));

        // act
        ApiException exception = catchThrowableOfType(() -> pushSubscriptionService.getVapidPublicKey(), ApiException.class);
        ResponseData later = pushSubscriptionService.getVapidPublicKey();

        // assert
        assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.PUSH_NOT_READY);
        assertThat(later.getData()).isEqualTo(Map.of("publicKey", "BLater"));
    }

    @Test
    @DisplayName("success case : 올바른 구독은 로그인 사용자 것으로 endpoint 기준 upsert 한다 (앞뒤 공백 제거)")
    void 올바른_구독은_로그인_사용자_것으로_upsert_한다() {
        // arrange
        when(pushEndpointPolicy.isValid("https://fcm.googleapis.com/x", "p", "a")).thenReturn(true);

        // act
        pushSubscriptionService.subscribe(new SubscribeRequestDto(" https://fcm.googleapis.com/x ", new SubscribeRequestDto.Keys("p ", " a")));

        // assert
        verify(webPushSubscriptionRepository).upsert(LOGIN_ID, "https://fcm.googleapis.com/x", "p", "a");
    }

    @Test
    @DisplayName("fail case : 허용하지 않은 endpoint 나 잘못된 키는 5001 로 거절하고 저장하지 않는다")
    void 허용하지_않은_endpoint_나_잘못된_키는_5001_로_거절한다() {
        // arrange
        when(pushEndpointPolicy.isValid(anyString(), anyString(), anyString())).thenReturn(false);

        // act
        ApiException exception = catchThrowableOfType(() -> pushSubscriptionService.subscribe(
            new SubscribeRequestDto("https://evil.example.com", new SubscribeRequestDto.Keys("p", "a"))), ApiException.class);

        // assert
        assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.INVALID_PUSH_SUBSCRIPTION);
        verify(webPushSubscriptionRepository, never()).upsert(anyLong(), any(), any(), any());
    }

    @Test
    @DisplayName("success case : 구독 해제는 내 구독만 지운다")
    void 구독_해제는_내_구독만_지운다() {
        // act
        pushSubscriptionService.unsubscribe(" https://fcm.googleapis.com/x ");

        // assert
        verify(webPushSubscriptionRepository).deleteByEndpointAndUserId("https://fcm.googleapis.com/x", LOGIN_ID);
    }

    @Test
    @DisplayName("fail case : 로그인하지 않으면 1003 이고 아무것도 읽거나 쓰지 않는다")
    void 로그인하지_않으면_1003_이고_아무것도_읽거나_쓰지_않는다() {
        // arrange
        SecurityContextHolder.clearContext();

        // act
        ApiException keyException = catchThrowableOfType(() -> pushSubscriptionService.getVapidPublicKey(), ApiException.class);
        ApiException unsubscribeException = catchThrowableOfType(() -> pushSubscriptionService.unsubscribe("https://fcm.googleapis.com/x"),
            ApiException.class);

        // assert
        assertThat(keyException.getErrorCode()).isEqualTo(ErrorCode.NOT_EXIST_USER_INFO);
        assertThat(unsubscribeException.getErrorCode()).isEqualTo(ErrorCode.NOT_EXIST_USER_INFO);
        verifyNoInteractions(vapidKeyRepository, webPushSubscriptionRepository);
    }

    private PushSubscriptionService service(String configuredPublicKey) {
        return new PushSubscriptionService(pushEndpointPolicy, webPushSubscriptionRepository, vapidKeyRepository, configuredPublicKey);
    }
}
