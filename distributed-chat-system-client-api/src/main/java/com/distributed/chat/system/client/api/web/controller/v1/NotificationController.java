package com.distributed.chat.system.client.api.web.controller.v1;

import com.distributed.chat.system.client.api.web.controller.v1.spec.NotificationControllerSpec;
import com.distributed.chat.system.client.api.web.dto.notification.SubscribeRequestDto;
import com.distributed.chat.system.client.api.web.dto.notification.UnsubscribeRequestDto;
import com.distributed.chat.system.client.api.web.service.PushSubscriptionService;
import com.distributed.chat.system.common.response.ResponseData;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 웹 푸시 구독 API. 보내기는 notification-consumer 가 한다 (chat-notification 소비)
 */
@RestController
@RequestMapping("/v1/private/api/notification")
@RequiredArgsConstructor
public class NotificationController implements NotificationControllerSpec {

    private final PushSubscriptionService pushSubscriptionService;

    /**
     * 웹 푸시 구독에 쓸 VAPID 공개키 (base64url)
     *
     * @return
     */
    @Override
    @GetMapping("/vapid-public-key")
    public ResponseEntity<?> getVapidPublicKey() {
        ResponseData responseData = pushSubscriptionService.getVapidPublicKey();

        return ResponseEntity.ok(responseData);
    }

    /**
     * 이 브라우저 구독 등록 (같은 endpoint 면 갱신)
     *
     * @param subscribeRequestDto
     * @return
     */
    @Override
    @PostMapping("/subscriptions")
    public ResponseEntity<?> subscribe(@RequestBody @Valid SubscribeRequestDto subscribeRequestDto) {
        ResponseData responseData = pushSubscriptionService.subscribe(subscribeRequestDto);

        return ResponseEntity.ok(responseData);
    }

    /**
     * 이 브라우저 구독 해제 (내 구독만)
     *
     * @param unsubscribeRequestDto
     * @return
     */
    @Override
    @DeleteMapping("/subscriptions")
    public ResponseEntity<?> unsubscribe(@RequestBody @Valid UnsubscribeRequestDto unsubscribeRequestDto) {
        ResponseData responseData = pushSubscriptionService.unsubscribe(unsubscribeRequestDto.getEndpoint());

        return ResponseEntity.ok(responseData);
    }
}
