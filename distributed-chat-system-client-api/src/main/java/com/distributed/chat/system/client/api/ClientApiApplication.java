package com.distributed.chat.system.client.api;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.openfeign.EnableFeignClients;

@SpringBootApplication(scanBasePackages = "com.distributed")
@EnableFeignClients
public class ClientApiApplication {

    public static void main(String[] args) {
        SpringApplication.run(ClientApiApplication.class, args);
    }
}