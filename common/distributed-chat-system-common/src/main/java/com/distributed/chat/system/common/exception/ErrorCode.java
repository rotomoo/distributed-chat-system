package com.distributed.chat.system.common.exception;

import static org.springframework.http.HttpStatus.BAD_REQUEST;
import static org.springframework.http.HttpStatus.FORBIDDEN;
import static org.springframework.http.HttpStatus.NOT_FOUND;
import static org.springframework.http.HttpStatus.PAYLOAD_TOO_LARGE;
import static org.springframework.http.HttpStatus.UNAUTHORIZED;

import lombok.AllArgsConstructor;
import lombok.Getter;
import org.springframework.http.HttpStatus;

@Getter
@AllArgsConstructor
public enum ErrorCode {

    /* 인증 */
    ALREADY_USING_ACCOUNT(1001, BAD_REQUEST, "이미 사용중인 계정입니다."),
    NOT_EXIST_ACCOUNT(1002, BAD_REQUEST, "없는 계정입니다."),
    NOT_EXIST_USER_INFO(1003, UNAUTHORIZED, "유저 정보가 존재하지 않습니다."),

    /* 팀(워크스페이스) */
    ALREADY_USING_TEAM_NAME(2001, BAD_REQUEST, "이미 존재하는 팀입니다."),
    NOT_EXIST_TEAM(2002, BAD_REQUEST, "없는 팀입니다."),
    NOT_TEAM_MEMBER(2003, FORBIDDEN, "팀 멤버가 아닙니다."),

    /* 채널 */
    ALREADY_USING_CHANNEL_NAME(3001, BAD_REQUEST, "이미 존재하는 채널입니다."),
    NOT_EXIST_CHANNEL(3002, BAD_REQUEST, "없는 채널입니다."),
    NOT_CHANNEL_MEMBER(3003, FORBIDDEN, "채널 멤버가 아닙니다."),
    CANNOT_DM_SELF(3004, BAD_REQUEST, "자기 자신에게는 DM 을 보낼 수 없습니다."),

    /* 메시지 */
    INVALID_MESSAGE_CURSOR(4001, BAD_REQUEST, "잘못된 메시지 위치입니다."),
    INVALID_CHAT_MESSAGE(4002, BAD_REQUEST, "잘못된 메시지 형식입니다."),

    /* 첨부파일 */
    FILE_TOO_LARGE(4003, PAYLOAD_TOO_LARGE, "파일이 너무 큽니다."),
    EMPTY_FILE(4004, BAD_REQUEST, "빈 파일입니다."),
    NOT_EXIST_FILE(4005, NOT_FOUND, "없는 파일입니다.");

    private final int customCode;
    private final HttpStatus httpStatus;
    private final String msg;
}
