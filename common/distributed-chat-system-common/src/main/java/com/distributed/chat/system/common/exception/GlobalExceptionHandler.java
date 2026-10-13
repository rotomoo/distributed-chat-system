package com.distributed.chat.system.common.exception;

import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler extends ResponseEntityExceptionHandler {

    @ExceptionHandler(ApiException.class)
    public ResponseEntity<ExceptionResponse> apiExceptionHandler(ApiException e) {
        log.error("ApiException : {}", e.getErrorCode().getMsg());
        return ExceptionResponse.toResponseEntity(e.getErrorCode());
    }

    /**
     * 업로드 크기 제한(spring.servlet.multipart.max-file-size)을 넘으면 컨트롤러에 오기 전에 여기로 온다
     */
    @Override
    protected ResponseEntity<Object> handleMaxUploadSizeExceededException(MaxUploadSizeExceededException e,
        HttpHeaders headers, HttpStatusCode status, WebRequest request) {
        log.warn("MaxUploadSizeExceededException : {}", e.getMessage());
        return new ResponseEntity<>(ExceptionResponse.toResponseEntity(ErrorCode.FILE_TOO_LARGE).getBody(),
            ErrorCode.FILE_TOO_LARGE.getHttpStatus());
    }

}
