package com.distributed.chat.system.connection.status;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication(scanBasePackages = "com.distributed")
public class ConnectionStatusApplication {

    public static void main(String[] args) {
        SpringApplication.run(ConnectionStatusApplication.class, args);
    }
}
