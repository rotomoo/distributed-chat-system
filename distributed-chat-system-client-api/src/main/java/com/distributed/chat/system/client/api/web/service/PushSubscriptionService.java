package com.distributed.chat.system.client.api.web.service;

import com.distributed.chat.system.client.api.base.security.SecurityUtil;
import com.distributed.chat.system.client.api.base.webpush.PushEndpointPolicy;
import com.distributed.chat.system.client.api.web.dto.notification.SubscribeRequestDto;
import com.distributed.chat.system.common.exception.ApiException;
import com.distributed.chat.system.common.exception.ErrorCode;
import com.distributed.chat.system.common.response.ResponseData;
import com.distributed.chat.system.mysql.entity.VapidKey;
import com.distributed.chat.system.mysql.repository.VapidKeyRepository;
import com.distributed.chat.system.mysql.repository.WebPushSubscriptionRepository;
import java.util.Map;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

/**
 * 웹 푸시 구독 (MySQL web_push_subscription). 쓰는 쪽은 여기 하나, 읽는 쪽은 notification-consumer (보낼 때)
 * <p>
 * VAPID 키는 notification-consumer 가 정한다 (환경변수 → MySQL vapid_key → 처음 뜰 때 생성). 여기서는 공개키만 같은 순서로 읽는다.
 * 키는 한 번 정하면 바뀌지 않으므로 처음 읽은 값을 계속 쓴다
 */
@Service
public class PushSubscriptionService {

    private final PushEndpointPolicy pushEndpointPolicy;
    private final WebPushSubscriptionRepository webPushSubscriptionRepository;
    private final VapidKeyRepository vapidKeyRepository;
    private final String configuredPublicKey;

    private volatile String storedPublicKey;

    public PushSubscriptionService(PushEndpointPolicy pushEndpointPolicy, WebPushSubscriptionRepository webPushSubscriptionRepository,
        VapidKeyRepository vapidKeyRepository, @Value("${chat.notification.vapid.public-key:}") String configuredPublicKey) {
        this.pushEndpointPolicy = pushEndpointPolicy;
        this.webPushSubscriptionRepository = webPushSubscriptionRepository;
        this.vapidKeyRepository = vapidKeyRepository;
        this.configuredPublicKey = configuredPublicKey == null ? "" : configuredPublicKey.strip();
    }

    /**
     * 브라우저가 pushManager.subscribe({applicationServerKey}) 에 넣을 공개키.
     * notification-consumer 가 아직 한 번도 뜨지 않아 키가 없으면 5002 (프론트는 웹 푸시만 건너뛴다)
     */
    public ResponseData getVapidPublicKey() {
        SecurityUtil.getLoginId();
        return ResponseData.success("VAPID 공개키 조회", Map.of("publicKey", vapidPublicKey()));
    }

    /**
     * 같은 endpoint 면 갱신 (주인도 로그인한 사람으로 바뀐다 : 그 브라우저에 지금 로그인한 사람이 받는다)
     */
    public ResponseData subscribe(SubscribeRequestDto request) {
        Long loginId = SecurityUtil.getLoginId();
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
    public ResponseData unsubscribe(String endpoint) {
        Long loginId = SecurityUtil.getLoginId();
        webPushSubscriptionRepository.deleteByEndpointAndUserId(endpoint.strip(), loginId);
        return ResponseData.success("웹 푸시 구독 해제", null);
    }

    private String vapidPublicKey() {
        if (!configuredPublicKey.isEmpty()) {
            return configuredPublicKey;
        }
        String cached = storedPublicKey;
        if (cached != null) {
            return cached;
        }
        String found = vapidKeyRepository.findPublicKeyById(VapidKey.SINGLE_ID)
            .orElseThrow(() -> new ApiException(ErrorCode.PUSH_NOT_READY));
        storedPublicKey = found;
        return found;
    }
}
