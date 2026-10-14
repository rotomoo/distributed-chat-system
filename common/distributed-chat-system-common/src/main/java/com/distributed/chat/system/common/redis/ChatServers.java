package com.distributed.chat.system.common.redis;

import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.cloud.client.discovery.DiscoveryClient;

/**
 * 지금 살아 있는 채팅 서버 (Eureka 에 등록된 chatting 인스턴스의 ip:port).
 * Eureka 클라이언트가 주기적으로 받아 메모리에 둔 목록이라 부를 때마다 네트워크를 타지 않는다.
 * <p>
 * Redis {@code {userId}} 가 여기에 없는 서버를 가리키면 그 서버는 죽은 것이므로 오프라인으로 본다
 * (채팅 서버가 죽으면 키를 지울 기회가 없으므로 읽는 쪽이 걸러낸다)
 */
public final class ChatServers {

    public static final String CHATTING_SERVICE_ID = "chatting";

    private ChatServers() {
    }

    public static Set<String> alive(DiscoveryClient discoveryClient) {
        return discoveryClient.getInstances(CHATTING_SERVICE_ID).stream()
            .map(instance -> address(instance.getHost(), instance.getPort()))
            .collect(Collectors.toSet());
    }

    public static String address(String host, int port) {
        return host + ":" + port;
    }
}
