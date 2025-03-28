package com.distributed.chat.system.common.interceptor;

import com.distributed.chat.system.common.filter.CustomHttpServletRequestWrapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.net.InetAddress;
import java.nio.charset.StandardCharsets;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.servlet.HandlerInterceptor;
import org.springframework.web.util.ContentCachingResponseWrapper;

@Slf4j
@Component
@RequiredArgsConstructor
public class LoggingInterceptor implements HandlerInterceptor {

    private static final String LOG_ID = "";
    private final ObjectMapper objectMapper;

    private boolean skipYn(String method, String uri) {
        return method.equals("GET") && (
            uri.contains("swagger") ||
                uri.contains("api-docs") ||
                uri.endsWith(".html") ||
                uri.endsWith(".css") ||
                uri.endsWith(".js") ||
                uri.equals("/")
        );
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws IOException {
        String requestMethod = request.getMethod();
        String requestURI = request.getRequestURI();
        if (skipYn(requestMethod, requestURI)) {
            return true;
        }
        String logId = UUID.randomUUID().toString();
        request.setAttribute(LOG_ID, logId);

        CustomHttpServletRequestWrapper requestWrapper = new CustomHttpServletRequestWrapper(request);

        String requestParams = request.getParameterMap()
            .entrySet()
            .stream()
            .map(entry -> entry.getKey() + "=" + String.join(",", entry.getValue()))
            .collect(Collectors.joining(", "));
        String requestBody = String.valueOf(
            objectMapper.readTree(new String(requestWrapper.getContents(), StandardCharsets.UTF_8)));

        log.info("\n" +
                "[REQUEST] {} - {} \n" +
                "* LogId : \n\t{}\n" +
                "* Headers : \n\t{}\n" +
                "* ServerIp : \n\t{}\n" +
                "* ClientIp : \n\t{}\n" +
                "* Request : \n\t{}\n",
            requestMethod,
            requestURI,
            logId,
            getHeaders(request),
            InetAddress.getLocalHost().getHostAddress(),
            StringUtils.hasText(request.getHeader("X-Forwarded-For"))
                ? request.getHeader("X-Forwarded-For")
                : request.getRemoteAddr(),
            requestParams.isBlank() ? requestBody : requestParams + ", " + requestBody
        );

        return true;
    }

    @Override
    public void afterCompletion(HttpServletRequest request, HttpServletResponse response, Object handler, Exception ex)
        throws Exception {
        String requestMethod = request.getMethod();
        String requestURI = request.getRequestURI();
        if (skipYn(requestMethod, requestURI)) {
            return;
        }
        ContentCachingResponseWrapper responseWrapper;
        if (response instanceof ContentCachingResponseWrapper) {
            responseWrapper = (ContentCachingResponseWrapper) response;
        } else {
            responseWrapper = new ContentCachingResponseWrapper(response);
        }

        String logId = (String) request.getAttribute(LOG_ID);

        log.info("\n" +
                "[RESPONSE] {} - {} \n" +
                "* LogId : \n\t{}\n" +
                "* Response : \n\t{}\n",
            requestMethod,
            requestURI,
            logId,
            new String(responseWrapper.getContentAsByteArray(), StandardCharsets.UTF_8)
        );
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
}
