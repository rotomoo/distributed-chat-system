package com.distributed.chat.system.notification.consumer.base.webpush;

import java.security.Security;
import org.bouncycastle.jce.provider.BouncyCastleProvider;

/**
 * web-push 라이브러리는 타원곡선 키를 BouncyCastle("BC") provider 로 읽고 만든다. 쓰기 전에 한 번 등록
 */
final class BouncyCastle {

    private BouncyCastle() {
    }

    static synchronized void register() {
        if (Security.getProvider(BouncyCastleProvider.PROVIDER_NAME) == null) {
            Security.addProvider(new BouncyCastleProvider());
        }
    }
}
