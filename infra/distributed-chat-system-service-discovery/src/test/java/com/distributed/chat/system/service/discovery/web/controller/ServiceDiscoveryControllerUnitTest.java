package com.distributed.chat.system.service.discovery.web.controller;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.cloud.client.ServiceInstance;
import org.springframework.cloud.client.loadbalancer.LoadBalancerClient;
import org.springframework.web.util.UriComponentsBuilder;

@DisplayName("[단위] ServiceDiscoveryController 테스트")
@ExtendWith(MockitoExtension.class)
class ServiceDiscoveryControllerUnitTest {

    @Mock
    private ServiceInstance serviceInstance;

    @Mock
    private LoadBalancerClient loadBalancerClient;

    @InjectMocks
    private ServiceDiscoveryController serviceDiscoveryController;

    @Test
    @DisplayName("true case: 로드밸런서 반환 성공")
    void getChattingServiceInstanceTrue() {
        // arrange
        when(serviceInstance.getUri()).thenReturn(
            UriComponentsBuilder.fromUriString("http://localhost:8080").build().toUri());
        when(loadBalancerClient.choose("chatting")).thenReturn(serviceInstance);

        // act
        String uri = serviceDiscoveryController.getChattingServiceInstance();

        // assert
        assertNotNull(uri);
        assertEquals("http://localhost:8080", uri);
    }

    @Test
    @DisplayName("true case: 가용 서버 없으면 null 반환")
    void getChattingServiceInstanceNull() {
        // arrange
        when(loadBalancerClient.choose("chatting")).thenReturn(null);

        // act
        String uri = serviceDiscoveryController.getChattingServiceInstance();

        // assert
        assertNull(uri);
    }
}