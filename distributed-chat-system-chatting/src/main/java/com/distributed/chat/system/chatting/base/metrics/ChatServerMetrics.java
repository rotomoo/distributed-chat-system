package com.distributed.chat.system.chatting.base.metrics;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import java.util.function.Supplier;

/**
 * 채팅 서버 메트릭 (부하 테스트 / 모니터링용, /actuator/prometheus 에서 chat_* 로 보인다)
 * <ul>
 *   <li>chat.websocket.sessions : 지금 열린 웹소켓 수 (기기 단위)</li>
 *   <li>chat.messages.received{type} : 클라이언트가 보낸 메시지 / 리액션 프레임 수 (형식 / 권한 검사 전)</li>
 *   <li>chat.messages.published{type} : 검사를 통과해 발행한 수</li>
 *   <li>chat.frames.sent : 소켓으로 보낸 프레임 수 (메시지 / 리액션 / ping)</li>
 *   <li>chat.send.failures : 보내다 실패해서 닫은 소켓 수</li>
 *   <li>chat.heartbeat.timeouts : 응답이 없어 닫은 소켓 수</li>
 * </ul>
 */
public class ChatServerMetrics {

    private final MeterRegistry meterRegistry;
    private final Counter framesSent;
    private final Counter sendFailures;
    private final Counter heartbeatTimeouts;

    public ChatServerMetrics(MeterRegistry meterRegistry) {
        this.meterRegistry = meterRegistry;
        this.framesSent = Counter.builder("chat.frames.sent").description("소켓으로 보낸 프레임 수").register(meterRegistry);
        this.sendFailures = Counter.builder("chat.send.failures").description("보내다 실패해서 닫은 소켓 수").register(meterRegistry);
        this.heartbeatTimeouts = Counter.builder("chat.heartbeat.timeouts").description("응답이 없어 닫은 소켓 수").register(meterRegistry);
    }

    /**
     * 열린 소켓 수는 그때그때 센다 (값을 따로 들고 있지 않음)
     */
    public void bindOpenSessions(Supplier<Number> openSessions) {
        Gauge.builder("chat.websocket.sessions", openSessions).description("열린 웹소켓 수").register(meterRegistry);
    }

    public void received(String type) {
        Counter.builder("chat.messages.received").tag("type", type).register(meterRegistry).increment();
    }

    public void published(String type) {
        Counter.builder("chat.messages.published").tag("type", type).register(meterRegistry).increment();
    }

    public void frameSent() {
        framesSent.increment();
    }

    public void sendFailed() {
        sendFailures.increment();
    }

    public void heartbeatTimedOut() {
        heartbeatTimeouts.increment();
    }
}
