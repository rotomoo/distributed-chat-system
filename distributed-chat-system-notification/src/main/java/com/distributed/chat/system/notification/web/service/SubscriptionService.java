package com.distributed.chat.system.notification.web.service;

import com.distributed.chat.system.common.exception.ApiException;
import com.distributed.chat.system.common.exception.ErrorCode;
import com.distributed.chat.system.common.response.ResponseData;
import com.distributed.chat.system.mysql.repository.WebPushSubscriptionRepository;
import com.distributed.chat.system.notification.base.webpush.PushEndpointPolicy;
import com.distributed.chat.system.notification.base.webpush.VapidKeys;
import com.distributed.chat.system.notification.web.dto.SubscribeRequestDto;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/**
 * 웹 푸시 구독 (MySQL web_push_subscription, 쓰는 쪽은 이 서버 하나)
 */
@Service
@RequiredArgsConstructor
public class SubscriptionService {

    private final VapidKeys vapidKeys;
    private final PushEndpointPolicy pushEndpointPolicy;
    private final WebPushSubscriptionRepository webPushSubscriptionRepository;

    /**
     * 브라우저가 pushManager.subscribe({applicationServerKey}) 에 넣을 공개키
     */
    public ResponseData getVapidPublicKey() {
        return ResponseData.success("VAPID 공개키 조회", Map.of("publicKey", vapidKeys.publicKey()));
    }

    /**
     * 같은 endpoint 면 갱신 (주인도 로그인한 사람으로 바뀐다 : 그 브라우저에 지금 로그인한 사람이 받는다)
     */
    public ResponseData subscribe(Long loginId, SubscribeRequestDto request) {
        String endpoint = request.getEndpoint().strip();
        String p256dh = request.getKeys().getP256dh().strip();
        String auth = request.getKeys().getAuth().strip();
        if (!pushEndpointPolicy.isValid(endpoint, p256dh, auth)) {
            throw new ApiException(ErrorCode.INVALID_PUSH_SUBSCRIPTION);
        }
        webPushSubscriptionRepository.upsert(loginId, endpoint, p256dh, auth);
        return ResponseData.success("웹 푸시 구독", null);
    }

    /**
     * 내 구독만 지운다. 없으면 그대로 성공 (이미 해제됨)
     */
    public ResponseData unsubscribe(Long loginId, String endpoint) {
        webPushSubscriptionRepository.deleteByEndpointAndUserId(endpoint.strip(), loginId);
        return ResponseData.success("웹 푸시 구독 해제", null);
    }
}
