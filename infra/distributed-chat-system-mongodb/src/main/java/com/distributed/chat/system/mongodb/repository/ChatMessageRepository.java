package com.distributed.chat.system.mongodb.repository;

import com.distributed.chat.system.mongodb.document.ChatMessage;
import org.springframework.data.mongodb.repository.MongoRepository;

public interface ChatMessageRepository extends MongoRepository<ChatMessage, String> {

}
