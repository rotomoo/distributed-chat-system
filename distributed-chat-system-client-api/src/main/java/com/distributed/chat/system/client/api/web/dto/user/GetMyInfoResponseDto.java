package com.distributed.chat.system.client.api.web.dto.user;

import com.distributed.chat.system.mysql.entity.User;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
public class GetMyInfoResponseDto {

    private Long userId;
    private String userName;
    private String account;
    private Boolean deleteYn;
    private String chatServerUrl;

    public GetMyInfoResponseDto(User user,
        String chatServerUrl) {
        this.userId = user.getId();
        this.userName = user.getUserName();
        this.account = user.getAccount();
        this.deleteYn = user.getDeleteYn();
        this.chatServerUrl = chatServerUrl;
    }
}
