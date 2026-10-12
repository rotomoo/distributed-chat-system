package com.distributed.chat.system.mongodb.config;

import com.mongodb.ConnectionString;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.mongodb.MongoDatabaseFactory;
import org.springframework.data.mongodb.core.SimpleMongoClientDatabaseFactory;

/**
 * 접속 정보는 프로필별 spring.data.mongodb.uri 로 결정 (application-mongodb.yml)
 */
@Configuration
public class MongoFactoryConfig {

    @Value("${spring.data.mongodb.uri}")
    private String mongoUri;

    @Bean(name = "notificationMongoFactory")
    public MongoDatabaseFactory notificationMongoFactory() {
        return new SimpleMongoClientDatabaseFactory(new ConnectionString(mongoUri));
    }
}
