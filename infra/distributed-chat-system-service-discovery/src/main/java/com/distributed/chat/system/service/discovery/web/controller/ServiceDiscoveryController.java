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

    @GetMapping("/chatting-service")
    public String getChattingServiceInstance() {
        ServiceInstance serviceInstance = loadBalancerClient.choose("chatting");
        return serviceInstance != null ? serviceInstance.getUri().toString() : null;
    }
}

