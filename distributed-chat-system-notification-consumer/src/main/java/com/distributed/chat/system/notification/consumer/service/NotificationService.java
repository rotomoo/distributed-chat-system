package com.distributed.chat.system.notification.consumer.service;

import com.distributed.chat.system.common.event.ChatNotificationEvent;
import com.distributed.chat.system.mysql.entity.Channel;
import com.distributed.chat.system.mysql.entity.User;
import com.distributed.chat.system.mysql.entity.WebPushSubscription;
import com.distributed.chat.system.mysql.repository.ChannelRepository;
import com.distributed.chat.system.mysql.repository.ChannelUserRepository;
import com.distributed.chat.system.mysql.repository.UserRepository;
import com.distributed.chat.system.mysql.repository.WebPushSubscriptionRepository;
import com.distributed.chat.system.notification.consumer.base.webpush.WebPushSender;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * 알림 이벤트 한 건(받는 사람 최대 500명)을 웹 푸시로 보낸다. 조회는 이벤트당 한 번씩만 (사람마다 하지 않는다)
 * <ol>
 *   <li>채널 (없거나 지워졌으면 버림)</li>
 *   <li>받는 사람 중 지금도 멤버이고 음소거하지 않은 사람 (MySQL channel_user.notification_muted)</li>
 *   <li>그 사람들의 구독 (MySQL web_push_subscription). 구독이 없는 사람은 여기서 빠진다</li>
 *   <li>(유저, 채널) 30초에 한 번 (Redis SET NX EX, 파이프라인 1번)</li>
 *   <li>구독마다 동시에 보내고 모두 끝날 때까지 기다린다. 404 / 410 (만료된 구독)은 지운다</li>
 * </ol>
 * 1 ~ 4 의 조회가 실패하면 예외 → 재시도 토픽. 5 의 개별 발송 실패는 다시 하지 않는다
 * (이미 30초 키를 잡았으므로 다시 해도 건너뛰고, 알림은 유실을 허용한다) — 로그만 남긴다
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class NotificationService {

    private final ChannelRepository channelRepository;
    private final ChannelUserRepository channelUserRepository;
    private final UserRepository userRepository;
    private final WebPushSubscriptionRepository webPushSubscriptionRepository;
    private final NotificationThrottle notificationThrottle;
    private final WebPushSender webPushSender;
    private final ObjectMapper objectMapper;

    /**
     * 구독 하나에 보낸 결과
     *
     * @param status 푸시 서비스 응답 코드 (보내지 못했으면 null)
     */
    public record PushResult(WebPushSubscription subscription, Integer status, Throwable error) {

        public boolean expired() {
            return status != null && (status == 404 || status == 410);
        }

        public boolean succeeded() {
            return status != null && status >= 200 && status < 300;
        }
    }

    /**
     * @return 보낸 푸시 결과 (테스트 / 로그용)
     */
    public List<PushResult> notify(ChatNotificationEvent notification) {
        List<Long> receivers = notification.receiverUserIds().stream().distinct().toList();
        if (receivers.isEmpty()) {
            return List.of();
        }

        Optional<Channel> channel = channelRepository.findByIdAndDeleteYnIsFalse(notification.channelId());
        if (channel.isEmpty()) {
            log.info("[notification] channel not found, dropped messageId : {}, channelId : {}", notification.messageId(), notification.channelId());
            return List.of();
        }

        List<Long> unmuted = channelUserRepository.findUnmutedMemberUserIds(notification.channelId(), receivers);
        if (unmuted.isEmpty()) {
            return List.of();
        }

        List<WebPushSubscription> subscriptions = webPushSubscriptionRepository.findAllByUserIdIn(unmuted);
        if (subscriptions.isEmpty()) {
            return List.of();
        }

        Set<Long> allowed = notificationThrottle.acquire(subscriptions.stream().map(WebPushSubscription::getUserId).distinct().toList(),
            notification.channelId());
        List<WebPushSubscription> targets = subscriptions.stream().filter(subscription -> allowed.contains(subscription.getUserId())).toList();
        if (targets.isEmpty()) {
            return List.of();
        }

        String senderName = userRepository.findById(notification.senderId()).map(NotificationService::displayName).orElse(null);
        byte[] payload = toJson(WebPushPayload.of(channel.get(), senderName, notification));
        String topic = WebPushPayload.tag(notification.channelId());

        List<CompletableFuture<PushResult>> sends = targets.stream()
            .map(subscription -> webPushSender.send(subscription, payload, topic)
                .handle((status, error) -> new PushResult(subscription, status, unwrap(error))))
            .toList();
        List<PushResult> results = sends.stream().map(CompletableFuture::join).toList();

        for (PushResult result : results) {
            if (result.expired()) {
                // 브라우저가 구독을 해제했거나 만료됨 → 다시 보내지 않게 지운다
                webPushSubscriptionRepository.deleteByEndpoint(result.subscription().getEndpoint());
                log.info("[notification] expired subscription deleted userId : {}, status : {}", result.subscription().getUserId(), result.status());
            } else if (!result.succeeded()) {
                // 일시적 오류 (429, 5xx, 연결 실패, 타임아웃) : 구독은 그대로 두고 이번 알림만 버린다
                log.warn("[notification] push fail userId : {}, messageId : {}, status : {}, error : {}", result.subscription().getUserId(),
                    notification.messageId(), result.status(), result.error() == null ? null : result.error().getMessage());
            }
        }
        return results;
    }

    private static String displayName(User user) {
        return user.getUserName() != null && !user.getUserName().isBlank() ? user.getUserName() : user.getAccount();
    }

    private byte[] toJson(WebPushPayload payload) {
        try {
            return objectMapper.writeValueAsBytes(payload);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException(e);
        }
    }

    private static Throwable unwrap(Throwable error) {
        return error instanceof CompletionException && error.getCause() != null ? error.getCause() : error;
    }
}
