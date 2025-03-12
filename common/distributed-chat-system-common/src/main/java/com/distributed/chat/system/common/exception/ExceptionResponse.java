package com.distributed.chat.system.common.exception;

import lombok.Builder;
import lombok.Getter;
import org.springframework.http.ResponseEntity;

import java.util.Map;

@Getter
@Builder
public class ExceptionResponse<T> {

    private int code;
    private String msg;
    private T data;
    private boolean success;


    public static ResponseEntity<ExceptionResponse> toResponseEntity(ErrorCode errorCode) {
        return ResponseEntity
                .status(errorCode.getHttpStatus())
                .body(ExceptionResponse.builder()
                        .code(errorCode.getCustomCode())
                        .msg(errorCode.getMsg())
                        .data(Map.of("errorCodeName", errorCode.name()))
                        .success(false)
                        .build()
                );
    }
}