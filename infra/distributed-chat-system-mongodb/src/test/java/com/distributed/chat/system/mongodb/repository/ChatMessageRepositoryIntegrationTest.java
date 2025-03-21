package com.distributed.chat.system.mongodb.repository;

import com.distributed.chat.system.mongodb.document.ChatMessage;
import java.time.Instant;
import java.util.Optional;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

@DisplayName("[통합] ChatMessageRepository 테스트")
@SpringBootTest
class ChatMessageRepositoryIntegrationTest {

    @Autowired
    private ChatMessageRepository chatMessageRepository;

    @Test
    @DisplayName("true case: findById 테스트")
    void findById() {
        // given
        Instant now = Instant.now();

        ChatMessage chat = ChatMessage.builder()
            .channelId(1L)
            .content("test")
            .eventPublishDt(now)
            .createDt(now)
            .updateDt(now)
            .deleteDt(null)
            .createUserId(1L)
            .build();
        chatMessageRepository.save(chat);

        // when
        Optional<ChatMessage> foundChatMessage = chatMessageRepository.findById(chat.getId());

        // then
        Assertions.assertTrue(foundChatMessage.isPresent());
        Assertions.assertEquals("test", foundChatMessage.get().getContent());
    }
}