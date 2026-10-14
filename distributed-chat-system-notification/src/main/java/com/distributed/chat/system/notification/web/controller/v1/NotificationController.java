package com.distributed.chat.system.notification.web.controller.v1;

import com.distributed.chat.system.common.response.ResponseData;
import com.distributed.chat.system.notification.web.dto.SubscribeRequestDto;
import com.distributed.chat.system.notification.web.dto.UnsubscribeRequestDto;
import com.distributed.chat.system.notification.web.service.SubscriptionService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 로그인 사용자 = 공유 세션의 Authentication (name = userId, client-api 로그인과 같은 값)
 */
@RestController
@RequestMapping("/v1/private/api/notification")
@RequiredArgsConstructor
public class NotificationController {

    private final SubscriptionService subscriptionService;

    /**
     * 웹 푸시 구독에 쓸 VAPID 공개키 (base64url)
     */
    @GetMapping("/vapid-public-key")
    public ResponseEntity<?> getVapidPublicKey() {
        return ResponseEntity.ok(subscriptionService.getVapidPublicKey());
    }

    /**
     * 이 브라우저 구독 등록 (같은 endpoint 면 갱신)
     */
    @PostMapping("/subscriptions")
    public ResponseEntity<?> subscribe(Authentication authentication, @RequestBody @Valid SubscribeRequestDto subscribeRequestDto) {
        ResponseData responseData = subscriptionService.subscribe(loginId(authentication), subscribeRequestDto);

        return ResponseEntity.ok(responseData);
    }

    /**
     * 이 브라우저 구독 해제 (내 구독만)
     */
    @DeleteMapping("/subscriptions")
    public ResponseEntity<?> unsubscribe(Authentication authentication, @RequestBody @Valid UnsubscribeRequestDto unsubscribeRequestDto) {
        ResponseData responseData = subscriptionService.unsubscribe(loginId(authentication), unsubscribeRequestDto.getEndpoint());

        return ResponseEntity.ok(responseData);
    }

    private static Long loginId(Authentication authentication) {
        return Long.parseLong(authentication.getName());
    }
}
