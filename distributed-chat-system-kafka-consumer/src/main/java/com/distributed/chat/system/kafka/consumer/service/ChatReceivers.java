package com.distributed.chat.system.kafka.consumer.service;

import java.util.List;
import java.util.Map;

/**
 * 채널 멤버를 접속 위치로 나눈 결과 (Redis MGET 1번 + Eureka 생존 확인)
 *
 * @param byChatServer 접속한 멤버 : 채팅 서버(ip:port) → 그 서버에 붙은 멤버 (실시간 전달 대상)
 * @param offline      오프라인 멤버 : 위치 값이 없거나 죽은 서버를 가리킴 (웹 푸시 알림 대상)
 */
public record ChatReceivers(Map<String, List<Long>> byChatServer, List<Long> offline) {
}
