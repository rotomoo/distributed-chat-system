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
import org.springframework.web.util.WebUtils;

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

        String requestParams = request.getParameterMap()
            .entrySet()
            .stream()
            .map(entry -> entry.getKey() + "=" + String.join(",", entry.getValue()))
            .collect(Collectors.joining(", "));
        // JSON 이 아닌 바디(protobuf 등 바이너리)는 파싱하지 않고 크기만 남긴다
        // 본문은 LoggingFilter 가 복사해 둔 것만 쓴다. 필터가 건너뛴 요청(multipart, skip-body-paths)의 원래 스트림은 건드리지 않는다
        // (여기서 읽어 버리면 그 뒤의 컨트롤러가 본문을 읽지 못한다)
        String contentType = request.getContentType();
        CustomHttpServletRequestWrapper cachedRequest = WebUtils.getNativeRequest(request, CustomHttpServletRequestWrapper.class);
        String requestBody;
        if (cachedRequest == null) {
            requestBody = "<not cached, " + contentType + ", " + request.getContentLengthLong() + " bytes>";
        } else {
            requestBody = contentType == null || contentType.contains("json")
                ? String.valueOf(objectMapper.readTree(new String(cachedRequest.getContents(), StandardCharsets.UTF_8)))
                : "<" + contentType + ", " + cachedRequest.getContents().length + " bytes>";
        }

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
        // LoggingFilter 가 복사하지 않은 응답(파일 내려받기 등)은 본문을 남기지 않는다
        ContentCachingResponseWrapper responseWrapper = WebUtils.getNativeResponse(response, ContentCachingResponseWrapper.class);
        String responseBody = responseWrapper == null
            ? "<not cached>"
            : new String(responseWrapper.getContentAsByteArray(), StandardCharsets.UTF_8);

        String logId = (String) request.getAttribute(LOG_ID);

        log.info("\n" +
                "[RESPONSE] {} - {} \n" +
                "* LogId : \n\t{}\n" +
                "* Response : \n\t{}\n",
            requestMethod,
            requestURI,
            logId,
            responseBody
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
