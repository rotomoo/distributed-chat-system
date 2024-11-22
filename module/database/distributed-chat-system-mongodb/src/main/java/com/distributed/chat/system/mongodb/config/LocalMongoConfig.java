package com.distributed.chat.system.mongodb.config;

import com.mongodb.ConnectionString;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.mongodb.MongoDatabaseFactory;
import org.springframework.data.mongodb.core.SimpleMongoClientDatabaseFactory;
import org.testcontainers.containers.GenericContainer;

@Configuration
@Slf4j
public class LocalMongoConfig {

    private static final String MONGODB_IMAGE_NAME = "mongo";
    private static final int MONGODB_IMAGE_PORT = 27017;
    private static final String DATABASE_NAME = "notification";
    private static final GenericContainer mongo = createMongoInstance();

    private static GenericContainer createMongoInstance() {
        return new GenericContainer(MONGODB_IMAGE_NAME)
            .withExposedPorts(MONGODB_IMAGE_PORT)
            .withReuse(true);
    }

    @PostConstruct
    public void startMongo() {
        try {
            createMongoInstance().start();
        } catch (Exception e) {
            log.error(e.getMessage(), e);
        }
    }

    @PreDestroy
    public void stopMongo() {
        try {
            mongo.stop();
        } catch (Exception e) {
            log.error(e.getMessage(), e);
        }
    }

    @Bean(name = "notificationMongoFactory")
    public MongoDatabaseFactory notificationMongoFactory() {
        return new SimpleMongoClientDatabaseFactory(connectionString());
    }

    private ConnectionString connectionString() {
        String host = mongo.getHost();
        Integer port = mongo.getMappedPort(MONGODB_IMAGE_PORT);
        return new ConnectionString("mongoddb://" + host + ":" + port + "/" + DATABASE_NAME);
    }
}
