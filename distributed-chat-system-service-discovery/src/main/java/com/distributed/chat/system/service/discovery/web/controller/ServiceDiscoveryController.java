package com.distributed.chat.system.service.discovery.web.controller;

import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.cloud.client.ServiceInstance;
import org.springframework.cloud.client.discovery.DiscoveryClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@RequestMapping("/v1/public/api/service-discovery")
public class ServiceDiscoveryController {

    private final DiscoveryClient discoveryClient;

    @GetMapping("/chat-server")
    public String getChatServiceInstance() {
        List<ServiceInstance> instances = discoveryClient.getInstances("ChatServer");
        if (instances == null || instances.isEmpty()) {
            return null;
        }
        return instances.get(0).getUri().toString();
    }
}

