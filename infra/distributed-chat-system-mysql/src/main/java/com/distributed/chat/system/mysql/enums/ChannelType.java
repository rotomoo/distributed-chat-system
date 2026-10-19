package com.distributed.chat.system.mysql.enums;

/**
 * 채널 종류 (슬랙 기준)
 */
public enum ChannelType {
    /** 팀 안의 공개 채널. 누구나 목록에서 보고 참여할 수 있다 */
    PUBLIC,
    /** 1:1 다이렉트 메시지. 채널 이름은 dm:{작은 userId}:{큰 userId} */
    DM
}
