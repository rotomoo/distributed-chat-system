package com.distributed.chat.system.mongodb.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.mongodb.MongoDatabaseFactory;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.convert.MongoConverter;
import org.springframework.data.mongodb.gridfs.GridFsTemplate;
import org.springframework.data.mongodb.repository.config.EnableMongoRepositories;

@Configuration
@EnableMongoRepositories(
    basePackages = "com.distributed.chat.system.mongodb",
    mongoTemplateRef = MongoTemplateConfig.MONGO_TEMPLATE
)
public class MongoTemplateConfig {

    public static final String MONGO_TEMPLATE = "notificationMongoTemplate";

    @Bean(name = MONGO_TEMPLATE)
    public MongoTemplate notificationMongoTemplate(
        MongoDatabaseFactory notificationMongoFactory,
        MongoConverter mongoConverter
    ) {
        return new MongoTemplate(notificationMongoFactory, mongoConverter);
    }

    /**
     * 채팅 첨부파일 (GridFS 기본 버킷 fs)
     */
    @Bean
    public GridFsTemplate chatGridFsTemplate(MongoDatabaseFactory notificationMongoFactory, MongoConverter mongoConverter) {
        return new GridFsTemplate(notificationMongoFactory, mongoConverter);
    }
}
