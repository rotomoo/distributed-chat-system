package com.distributed.chat.system.notification.consumer.base.webpush;

import com.distributed.chat.system.mysql.repository.VapidKeyRepository;
import java.net.http.HttpClient;
import java.security.GeneralSecurityException;
import java.time.Duration;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * VAPID 키는 뜰 때 한 번 정한다 (환경변수 → MySQL vapid_key → 새로 만들어 저장). 키 쌍이 틀리면 뜨지 않는다
 */
@Configuration
public class WebPushConfig {

    @Bean
    public VapidKeys vapidKeys(VapidKeyRepository vapidKeyRepository,
        @Value("${chat.notification.vapid.public-key:}") String publicKey,
        @Value("${chat.notification.vapid.private-key:}") String privateKey,
        @Value("${chat.notification.vapid.subject:mailto:admin@example.com}") String subject) {
        return new VapidKeyLoader(vapidKeyRepository, publicKey, privateKey, subject).load();
    }

    @Bean
    public WebPushRequestFactory webPushRequestFactory(VapidKeys vapidKeys) throws GeneralSecurityException {
        return new WebPushRequestFactory(vapidKeys);
    }

    @Bean
    public WebPushSender webPushSender(WebPushRequestFactory webPushRequestFactory,
        @Value("${chat.notification.push.ttl-seconds:86400}") int ttlSeconds,
        @Value("${chat.notification.push.connect-timeout-ms:3000}") long connectTimeoutMs,
        @Value("${chat.notification.push.request-timeout-ms:5000}") long requestTimeoutMs) {
        HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofMillis(connectTimeoutMs))
            .followRedirects(HttpClient.Redirect.NEVER)
            .build();
        return new WebPushSender(webPushRequestFactory, httpClient, ttlSeconds, Duration.ofMillis(requestTimeoutMs));
    }
}
