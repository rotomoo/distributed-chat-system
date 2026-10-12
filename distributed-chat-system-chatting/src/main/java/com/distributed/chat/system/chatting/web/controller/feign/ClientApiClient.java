package com.distributed.chat.system.chatting.web.controller.feign;

import java.util.List;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

@FeignClient(name = "client-api", path = "/internal/channel")
public interface ClientApiClient {

    /**
     * 채널 멤버 Redis 캐시를 MySQL 기준으로 다시 채우고 멤버 ID 를 받는다
     */
    @GetMapping("/{channelId}/members")
    List<Long> refreshChannelMembers(@PathVariable("channelId") Long channelId);
}
