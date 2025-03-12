package com.distributed.chat.system.service.discovery.web.controller;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.cloud.client.DefaultServiceInstance;
import org.springframework.cloud.client.discovery.simple.SimpleDiscoveryProperties;

@DisplayName("[통합] ServiceDiscoveryController 테스트")
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class ServiceDiscoveryControllerInterTest {

    @Autowired
    private TestRestTemplate testRestTemplate;

    @Autowired
    private SimpleDiscoveryProperties simpleDiscoveryProperties;

    @Test
    @DisplayName("true case: 로드밸런서 라운드로빈 테스트")
    void getChattingServiceInstanceRoundRobinTrue() {
        // given
        String url = "/chatting-service";

        simpleDiscoveryProperties.getInstances().put("chatting", List.of(
            new DefaultServiceInstance("chatting1", "chatting", "localhost", 7777, true),
            new DefaultServiceInstance("chatting2", "chatting", "localhost", 7778, true),
            new DefaultServiceInstance("chatting3", "chatting", "localhost", 7779, true)
        ));

        Map<String, Integer> getInstanceCnts = new HashMap<>();

        // when
        for (int i = 0; i < 6; i++) {
            String response = testRestTemplate.getForObject(url, String.class);
            getInstanceCnts.put(response, getInstanceCnts.getOrDefault(response, 0) + 1);
        }

        // then
        assertThat(getInstanceCnts.get("https://localhost:7777")).isEqualTo(2);
        assertThat(getInstanceCnts.get("https://localhost:7778")).isEqualTo(2);
        assertThat(getInstanceCnts.get("https://localhost:7779")).isEqualTo(2);
    }
}