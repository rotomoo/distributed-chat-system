package com.distributed.chat.system.chatting.base.heartbeat;

import com.distributed.chat.system.chatting.base.handler.CustomTextWebSocketHandler;
import java.time.Duration;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * 앱 수준 하트비트. 브라우저 JS 는 웹소켓 프로토콜 ping/pong 을 볼 수 없어서 {"type":"ping"} 프레임을 보내고 {"type":"pong"} 을 받는다.
 * <ul>
 *   <li>chat.heartbeat.ping-interval-ms (기본 25초) 마다 열린 소켓 전부에 ping</li>
 *   <li>chat.heartbeat.timeout-ms (기본 60초) 동안 아무 프레임도 오지 않은 소켓은 닫는다 (SESSION_NOT_RELIABLE)
 *       → 클라이언트가 다시 붙고 빈틈을 채운다. 확인은 ping 주기마다 하므로 실제로는 timeout ~ timeout + 주기 사이에 닫힌다</li>
 * </ul>
 * 프록시 / 로드밸런서의 유휴 연결 끊김도 막는다 (25초마다 트래픽이 있음)
 */
@Component
@Slf4j
public class ChatHeartbeatScheduler {

    private final CustomTextWebSocketHandler customTextWebSocketHandler;
    private final Duration timeout;

    public ChatHeartbeatScheduler(CustomTextWebSocketHandler customTextWebSocketHandler,
        @Value("${chat.heartbeat.timeout-ms:60000}") long timeoutMs) {
        this.customTextWebSocketHandler = customTextWebSocketHandler;
        this.timeout = Duration.ofMillis(timeoutMs);
    }

    @Scheduled(fixedRateString = "${chat.heartbeat.ping-interval-ms:25000}", initialDelayString = "${chat.heartbeat.ping-interval-ms:25000}")
    public void heartbeat() {
        customTextWebSocketHandler.heartbeat(timeout);
    }
}
