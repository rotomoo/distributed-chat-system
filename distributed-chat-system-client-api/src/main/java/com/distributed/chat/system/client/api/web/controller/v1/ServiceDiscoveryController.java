package com.distributed.chat.system.client.api.web.controller.v1;

import com.distributed.chat.system.client.api.web.controller.feign.ServiceDiscoveryClient;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/v1/public/api/service-discovery")
@RequiredArgsConstructor
@Slf4j
public class ServiceDiscoveryController {

    private final ServiceDiscoveryClient serviceDiscoveryClient;

    @GetMapping("/chat-server")
    public ResponseEntity<?> chatServer() {
        String chatServiceUri = serviceDiscoveryClient.getChatServiceUri();

        return new ResponseEntity<>(chatServiceUri, HttpStatus.OK);
    }
}
