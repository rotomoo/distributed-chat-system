package com.distributed.chat.system.common.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.converter.protobuf.ProtobufHttpMessageConverter;

/**
 * 서버간 HTTP 통신(feign, RestClient)에서 프로토콜 버퍼 메시지를 application/x-protobuf 로 주고받기 위한 컨버터
 */
@Configuration
public class ProtobufConfig {

    @Bean
    public ProtobufHttpMessageConverter protobufHttpMessageConverter() {
        return new ProtobufHttpMessageConverter();
    }
}
