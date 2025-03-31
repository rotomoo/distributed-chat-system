package com.distributed.chat.system.common.exception;

import static org.springframework.http.HttpStatus.BAD_REQUEST;
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

    /* 팀 */
    ALREADY_USING_TEAM_NAME(2001, BAD_REQUEST, "이미 존재하는 팀입니다.");

    private final int customCode;
    private final HttpStatus httpStatus;
    private final String msg;
}
