package com.distributed.chat.system.client.api.web.service;

import com.distributed.chat.system.common.exception.ApiException;
import com.distributed.chat.system.common.exception.ErrorCode;
import com.distributed.chat.system.mysql.repository.ChannelUserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * 채널 멤버 확인 (MySQL channel_user, 나가지 않은 멤버). 메시지 / 파일 API 가 같이 쓴다
 */
@Component
@RequiredArgsConstructor
public class ChannelMemberGuard {

    private final ChannelUserRepository channelUserRepository;

    /**
     * 멤버가 아니면 3003
     */
    public void requireMember(Long channelId, Long userId) {
        if (channelId == null || !channelUserRepository.existsByChannelIdAndUserIdAndDeleteYnIsFalse(channelId, userId)) {
            throw new ApiException(ErrorCode.NOT_CHANNEL_MEMBER);
        }
    }
}
