package com.distributed.chat.system.notification.consumer;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication(scanBasePackages = "com.distributed")
public class NotificationConsumerApplication {

    public static void main(String[] args) {
        SpringApplication.run(NotificationConsumerApplication.class, args);
    }
}
