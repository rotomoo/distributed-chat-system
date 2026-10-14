package com.distributed.chat.system.notification.base.webpush;

import java.net.URI;
import java.util.Base64;
import java.util.List;
import java.util.Locale;

/**
 * 구독으로 받을 수 있는 값인지. 서버가 endpoint 로 POST 하므로 아무 URL 이나 받으면 내부망으로 요청을 보내게 된다 (SSRF)
 * <ul>
 *   <li>endpoint : https (테스트에서만 http 허용), 호스트가 허용 목록 (".push.apple.com" 처럼 점으로 시작하면 하위 도메인)</li>
 *   <li>p256dh : base64url 비압축 P-256 공개키 65바이트 (0x04 로 시작)</li>
 *   <li>auth : base64url 16바이트</li>
 * </ul>
 */
public class PushEndpointPolicy {

    static final int MAX_ENDPOINT_LENGTH = 1000;
    private static final int P256DH_LENGTH = 65;
    private static final int AUTH_LENGTH = 16;

    private final List<String> allowedHosts;
    private final boolean allowInsecure;

    public PushEndpointPolicy(List<String> allowedHosts, boolean allowInsecure) {
        this.allowedHosts = allowedHosts.stream().map(String::strip).map(host -> host.toLowerCase(Locale.ROOT))
            .filter(host -> !host.isEmpty()).toList();
        this.allowInsecure = allowInsecure;
    }

    public boolean isValid(String endpoint, String p256dh, String auth) {
        return isAllowedEndpoint(endpoint)
            && decodedLength(p256dh) == P256DH_LENGTH && Base64.getUrlDecoder().decode(pad(p256dh))[0] == 0x04
            && decodedLength(auth) == AUTH_LENGTH;
    }

    boolean isAllowedEndpoint(String endpoint) {
        if (endpoint == null || endpoint.length() > MAX_ENDPOINT_LENGTH) {
            return false;
        }
        URI uri;
        try {
            uri = URI.create(endpoint);
        } catch (IllegalArgumentException e) {
            return false;
        }
        String scheme = uri.getScheme() == null ? "" : uri.getScheme().toLowerCase(Locale.ROOT);
        if (!(scheme.equals("https") || (allowInsecure && scheme.equals("http"))) || uri.getHost() == null || uri.getUserInfo() != null) {
            return false;
        }
        String host = uri.getHost().toLowerCase(Locale.ROOT);
        return allowedHosts.stream().anyMatch(allowed -> allowed.startsWith(".") ? host.endsWith(allowed) : host.equals(allowed));
    }

    private static int decodedLength(String base64Url) {
        if (base64Url == null || base64Url.isBlank()) {
            return -1;
        }
        try {
            return Base64.getUrlDecoder().decode(pad(base64Url)).length;
        } catch (IllegalArgumentException e) {
            return -1;
        }
    }

    /**
     * 브라우저는 패딩 없이 주기도, 있게 주기도 한다
     */
    private static String pad(String base64Url) {
        String stripped = base64Url.strip().replace("=", "");
        return stripped + "=".repeat((4 - stripped.length() % 4) % 4);
    }
}
