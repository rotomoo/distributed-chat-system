package com.distributed.chat.system.client.api.web.controller.feign;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;

@FeignClient(name = "service-discovery-client", url = "${service-discovery.url}")
public interface ServiceDiscoveryClient {

    @GetMapping(value = "/chat-server")
    String getChatServiceUrl();
}
