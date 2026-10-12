package com.distributed.chat.system.client.api.web.dto.authentication;

import com.distributed.chat.system.mysql.entity.User;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 회원가입 응답. 엔티티를 그대로 내보내면 비밀번호 해시까지 응답(과 응답 로그)에 실리므로 필요한 값만 담는다.
 */
@Getter
@NoArgsConstructor
public class SignupResponseDto {

    private Long userId;
    private String account;
    private String userName;

    public SignupResponseDto(User user) {
        this.userId = user.getId();
        this.account = user.getAccount();
        this.userName = user.getUserName();
    }
}
