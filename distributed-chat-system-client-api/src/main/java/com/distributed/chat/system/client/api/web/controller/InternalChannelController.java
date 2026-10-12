package com.distributed.chat.system.client.api.web.controller;

import com.distributed.chat.system.client.api.web.service.ChannelService;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 서버간 내부 통신용. api-gateway 는 /v1/**, /websocket/** 만 라우팅하므로 외부에 노출되지 않는다.
 */
@RestController
@RequestMapping("/internal/channel")
@RequiredArgsConstructor
public class InternalChannelController {

    private final ChannelService channelService;

    /**
     * 채널 멤버 Redis 캐시를 MySQL 기준으로 다시 채운다
     */
    @GetMapping("/{channelId}/members")
    public List<Long> refreshMembers(@PathVariable Long channelId) {
        return channelService.refreshMemberCache(channelId);
    }
}
