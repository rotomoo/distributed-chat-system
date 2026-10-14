package com.distributed.chat.system.common.event;

/**
 * 이벤트로 읽을 수 없는 값 (JSON 문법 오류, 이벤트 모양이 아님, 빈 값, 예전 바이너리 형식으로 남은 값 등).
 * 다시 읽어도 같으므로 Kafka 컨슈머는 재시도하지 않고 바로 DLT 로 보낸다 (재시도 토픽 설정의 notRetryOn)
 */
public class MalformedEventException extends RuntimeException {

    public MalformedEventException(String message) {
        super(message);
    }

    public MalformedEventException(String message, Throwable cause) {
        super(message, cause);
    }
}
