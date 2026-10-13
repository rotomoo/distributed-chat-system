package com.distributed.chat.system.kafka.consumer.service;

import java.util.List;
import lombok.Getter;

/**
 * 채팅 서버 전달 실패 (저장은 끝난 뒤). 재시도 토픽으로 다시 전달하고, 끝내 실패해서 DLT 에 가면 "전달 버림" 으로 센다
 * (메시지는 MongoDB 에 있으므로 클라이언트가 재접속 / 빈틈 채우기로 받는다)
 */
@Getter
public class DeliveryFailedException extends RuntimeException {

    private final List<String> failedChatServers;

    public DeliveryFailedException(List<String> failedChatServers) {
        super("deliver fail chatServers : " + failedChatServers);
        this.failedChatServers = List.copyOf(failedChatServers);
    }
}
