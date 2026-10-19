package com.distributed.chat.system.notification.consumer.base.webpush;

import java.io.IOException;
import java.security.GeneralSecurityException;
import java.security.InvalidKeyException;
import nl.martijndwars.webpush.AbstractPushService;
import nl.martijndwars.webpush.Encoding;
import nl.martijndwars.webpush.HttpRequest;
import nl.martijndwars.webpush.Notification;
import nl.martijndwars.webpush.Utils;
import org.jose4j.lang.JoseException;

/**
 * web-push 라이브러리로 요청(암호화된 본문 + VAPID 서명 헤더)만 만든다. 보내기는 {@link WebPushSender} 가 공유 HTTP 클라이언트로 한다
 * (라이브러리의 PushService.send 는 보낼 때마다 HTTP 클라이언트를 새로 만들고 타임아웃을 정할 수 없다)
 */
public class WebPushRequestFactory extends AbstractPushService<WebPushRequestFactory> {

    /**
     * @throws GeneralSecurityException 키 형식이 틀리거나 공개키 / 개인키가 한 쌍이 아니면 (뜰 때 바로 실패)
     */
    public WebPushRequestFactory(VapidKeys vapidKeys) throws GeneralSecurityException {
        super(registered(vapidKeys).publicKey(), vapidKeys.privateKey(), vapidKeys.subject());
        // 라이브러리는 보낼 때가 되어서야 확인한다. 설정 실수는 뜰 때 바로 알 수 있게 여기서 확인
        if (!Utils.verifyKeyPair(getPrivateKey(), getPublicKey())) {
            throw new InvalidKeyException("VAPID public key and private key do not match");
        }
    }

    /**
     * RFC 8291 (aes128gcm) 로 암호화한 본문과 헤더 (TTL, Topic, Content-Encoding, Authorization: vapid t=..., k=...).
     * 라이브러리는 JWT aud 를 endpoint 의 scheme://host 로 만든다 (포트 없음. 실제 푸시 서비스는 443 이라 문제없다)
     */
    public HttpRequest prepare(Notification notification) throws GeneralSecurityException, IOException, JoseException {
        return prepareRequest(notification, Encoding.AES128GCM);
    }

    private static VapidKeys registered(VapidKeys vapidKeys) {
        BouncyCastle.register();
        return vapidKeys;
    }
}
