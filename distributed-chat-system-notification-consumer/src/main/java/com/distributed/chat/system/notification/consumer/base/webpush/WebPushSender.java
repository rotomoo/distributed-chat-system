package com.distributed.chat.system.notification.consumer.base.webpush;

import com.distributed.chat.system.mysql.entity.WebPushSubscription;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest.BodyPublishers;
import java.net.http.HttpResponse.BodyHandlers;
import java.time.Duration;
import java.util.concurrent.CompletableFuture;
import nl.martijndwars.webpush.HttpRequest;
import nl.martijndwars.webpush.Notification;

/**
 * 구독 하나에 웹 푸시 한 건. 본문 암호화 / VAPID 서명은 {@link WebPushRequestFactory}, 전송은 공유 {@link HttpClient} (비동기, 타임아웃)
 */
public class WebPushSender {

    private final WebPushRequestFactory requestFactory;
    private final HttpClient httpClient;
    private final int ttlSeconds;
    private final Duration requestTimeout;

    public WebPushSender(WebPushRequestFactory requestFactory, HttpClient httpClient, int ttlSeconds, Duration requestTimeout) {
        this.requestFactory = requestFactory;
        this.httpClient = httpClient;
        this.ttlSeconds = ttlSeconds;
        this.requestTimeout = requestTimeout;
    }

    /**
     * 결과 : 푸시 서비스 응답 코드 (201 이 정상). 암호화 실패 / 연결 실패 / 타임아웃은 예외로 끝난 future
     *
     * @param topic 같은 topic 의 아직 전달 안 된 푸시는 푸시 서비스가 마지막 것만 남긴다 (채널 단위로 쌓이지 않게)
     */
    public CompletableFuture<Integer> send(WebPushSubscription subscription, byte[] payload, String topic) {
        try {
            HttpRequest request = requestFactory.prepare(Notification.builder()
                .endpoint(subscription.getEndpoint())
                .userPublicKey(subscription.getP256dh())
                .userAuth(subscription.getAuth())
                .payload(payload)
                .ttl(ttlSeconds)
                .topic(topic)
                .build());

            java.net.http.HttpRequest.Builder builder = java.net.http.HttpRequest.newBuilder(URI.create(request.getUrl()))
                .timeout(requestTimeout)
                .POST(BodyPublishers.ofByteArray(request.getBody()));
            request.getHeaders().forEach(builder::header);
            return httpClient.sendAsync(builder.build(), BodyHandlers.discarding()).thenApply(java.net.http.HttpResponse::statusCode);
        } catch (Exception e) {
            return CompletableFuture.failedFuture(e);
        }
    }
}
