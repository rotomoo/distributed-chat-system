package com.distributed.chat.system.connection.status.base.security;

import com.distributed.chat.system.common.exception.ApiException;
import com.distributed.chat.system.common.exception.ErrorCode;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

/**
 * 로그인 사용자 ID. client-api 가 로그인할 때 Authentication 이름을 userId 로 넣는다
 */
public class SecurityUtil {

    public static Long getLoginId() {
        Authentication authentication = SecurityContextHolder.getContext()
            .getAuthentication();

        if (authentication == null || !authentication.isAuthenticated()
            || authentication.getPrincipal().equals("anonymousUser")) {
            throw new ApiException(ErrorCode.NOT_EXIST_USER_INFO);
        }

        return Long.parseLong(authentication.getName());
    }
}
