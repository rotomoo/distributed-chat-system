package com.distributed.chat.system.service.discovery.web.controller;

import lombok.RequiredArgsConstructor;
import org.springframework.cloud.client.ServiceInstance;
import org.springframework.cloud.client.loadbalancer.LoadBalancerClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
public class ServiceDiscoveryController {

    private final LoadBalancerClient loadBalancerClient;

    @GetMapping("/chat-server")
    public String getChatServiceInstance() {
        ServiceInstance serviceInstance = loadBalancerClient.choose("chat-server");
        return serviceInstance != null ? serviceInstance.getUri().toString() : null;
    }
}

