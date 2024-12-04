package com.distributed.chat.system.common.interceptor;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;
import org.springframework.web.util.ContentCachingRequestWrapper;

import java.util.Enumeration;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

@Slf4j
@Component
public class LoggingInterceptor implements HandlerInterceptor {

    private static final String LOG_ID = "";
    private ObjectMapper objectMapper;

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
        String uuid = UUID.randomUUID().toString();
        request.setAttribute(LOG_ID, uuid);

//        request.
//        objectMapper.readTree(req)

        log.info("\n" +
                        "[REQUEST] {} - {} \n" +
                        "* LOG_ID : \n\t{}\n" +
                        "* Headers : \n\t{}\n" +
                        "* Request Body : \n\t{}\n" +
                        request.getMethod(),
                request.getRequestURI(),
                uuid,
                getHeaders(request)
        );


        return true;
    }

    @Override
    public void afterCompletion(HttpServletRequest request, HttpServletResponse response, Object handler, Exception ex) throws Exception {
        String requestURI = request.getRequestURI();
        String uuid = (String) request.getAttribute(LOG_ID);
    }

    private Map getHeaders(HttpServletRequest request) {
        Map headerMap = new HashMap<>();

        Enumeration headerArray = request.getHeaderNames();
        while (headerArray.hasMoreElements()) {
            String headerName = (String) headerArray.nextElement();
            headerMap.put(headerName, request.getHeader(headerName));
        }
        return headerMap;
    }

    private String getRequestBody(ContentCachingRequestWrapper cachingRequest) {
        try {
            if (cachingRequest.getContentAsByteArray() != null
                    && cachingRequest.getContentAsByteArray().length != 0) {
                // TODO content-type 이 json 아닐때 처리
                return objectMapper.readTree(cachingRequest.getContentAsByteArray()).toString();
            } else {
                return "-";
            }
        } catch (Exception e) {
            e.printStackTrace();
            return "-";
        }
    }
}
