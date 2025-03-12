package com.distributed.chat.system.common.response;

import lombok.Getter;

@Getter
public class ResponseData<T> {
    private int code;
    private String msg;
    private T data;
    private boolean success;

    public ResponseData(int code, String msg, T data, boolean success) {
        this.code = code;
        this.msg = msg;
        this.data = data;
        this.success = success;
    }

    public static <T> ResponseData<T> success(String msg, T data) {
        return new ResponseData<T>(0, msg, data, true);
    }
}
