package com.distributed.chat.system.common.exception;

import lombok.AllArgsConstructor;
import lombok.Getter;
import org.springframework.http.HttpStatus;

import static org.springframework.http.HttpStatus.BAD_REQUEST;

@Getter
@AllArgsConstructor
public enum ErrorCode {

    ALREADY_USING_ACCOUNT(1001, BAD_REQUEST, "이미 사용중인 계정입니다."),
    NOT_EXIST_ACCOUNT(1002, BAD_REQUEST, "없는 계정입니다.");

    private final int customCode;
    private final HttpStatus httpStatus;
    private final String msg;
}
