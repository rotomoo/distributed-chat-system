package com.distributed.chat.system.notification.base.webpush;

/**
 * 웹 푸시 서버 식별 키 (VAPID, P-256). 브라우저는 publicKey 로 구독하고, 서버는 privateKey 로 서명한다
 *
 * @param publicKey  base64url, 비압축 65바이트 (브라우저 applicationServerKey)
 * @param privateKey base64url, 32바이트
 * @param subject    mailto: 또는 https: (푸시 서비스가 연락할 곳)
 */
public record VapidKeys(String publicKey, String privateKey, String subject) {

    @Override
    public String toString() {
        // 개인키는 로그에 남기지 않는다
        return "VapidKeys[publicKey=" + publicKey + ", subject=" + subject + "]";
    }
}
