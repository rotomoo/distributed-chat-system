package com.distributed.chat.system.common.exception;

import lombok.AllArgsConstructor;
import lombok.Getter;
import org.springframework.http.HttpStatus;

import static org.springframework.http.HttpStatus.BAD_REQUEST;

@Getter
@AllArgsConstructor
public enum ErrorCode {

    ALREADY_USE_ACCOUNT(1001, BAD_REQUEST, "이미 가입되어있는 계정입니다.");

    private final int customCode;
    private final HttpStatus httpStatus;
    private final String msg;
}
