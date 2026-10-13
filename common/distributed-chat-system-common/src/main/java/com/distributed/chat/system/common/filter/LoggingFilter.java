package com.distributed.chat.system.common.filter;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.List;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.util.AntPathMatcher;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.web.util.ContentCachingResponseWrapper;

/**
 * 요청/응답 본문을 메모리에 복사해 두고 로그로 남긴다 (LoggingInterceptor).
 * <p>
 * 파일 업로드(multipart)와 파일 내려받기처럼 본문이 큰 요청은 복사하지 않는다.
 * 복사하면 파일 전체가 메모리에 올라가고, 업로드는 원래 스트림이 소비되어 multipart 를 읽을 수 없게 된다
 */
@Component
public class LoggingFilter extends OncePerRequestFilter {

    private static final AntPathMatcher PATH_MATCHER = new AntPathMatcher();

    /**
     * 본문을 복사하지 않을 경로 (예: 파일 내려받기). 서비스별 application.yml 에서 정한다
     */
    private final List<String> skipBodyPaths;

    public LoggingFilter(@Value("${chat.logging.skip-body-paths:}") List<String> skipBodyPaths) {
        this.skipBodyPaths = skipBodyPaths.stream().filter(path -> !path.isBlank()).toList();
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return isMultipart(request) || skipBodyPaths.stream().anyMatch(path -> PATH_MATCHER.match(path, request.getRequestURI()));
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain) throws ServletException, IOException {
        CustomHttpServletRequestWrapper requestWrapper = new CustomHttpServletRequestWrapper(request);
        ContentCachingResponseWrapper responseWrapper = new ContentCachingResponseWrapper(response);

        filterChain.doFilter(requestWrapper, responseWrapper);

        responseWrapper.copyBodyToResponse();
    }

    public static boolean isMultipart(HttpServletRequest request) {
        String contentType = request.getContentType();
        return contentType != null && contentType.toLowerCase().startsWith("multipart/");
    }
}
