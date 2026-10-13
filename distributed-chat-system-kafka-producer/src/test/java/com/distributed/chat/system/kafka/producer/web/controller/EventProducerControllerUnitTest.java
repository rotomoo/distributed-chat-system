package com.distributed.chat.system.kafka.producer.web.controller;

import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.distributed.chat.system.kafka.producer.web.service.EventProducerService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.converter.protobuf.ProtobufHttpMessageConverter;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import protobuf.EdaChatMessage.ChatMessage;
import protobuf.EdaChatMessage.ChatReaction;

@DisplayName("[단위] EventProducerController 테스트")
@ExtendWith(MockitoExtension.class)
class EventProducerControllerUnitTest {

    @Mock
    private EventProducerService eventProducerService;

    @InjectMocks
    private EventProducerController eventProducerController;

    private MockMvc mockMvc;

    @BeforeEach
    void setup() {
        mockMvc = MockMvcBuilders.standaloneSetup(eventProducerController)
            .setMessageConverters(new ProtobufHttpMessageConverter())
            .build();
    }

    @Test
    @DisplayName("success case : protobuf 리액션을 받아 그대로 발행한다")
    void protobuf_리액션을_받아_그대로_발행한다() throws Exception {
        // arrange
        ChatReaction chatReaction = ChatReaction.newBuilder()
            .setSendUserId(5L).setChannelId(7L).setMessageId("6acc66675f99715e286f0f6e").setEmoji("🎉")
            .setAction(ChatReaction.Action.REMOVE).setPublishDt(1_700_000_000_000L).build();

        // act
        mockMvc.perform(post("/v1/private/event/publish/chat-reaction")
                .contentType("application/x-protobuf").content(chatReaction.toByteArray()))
            .andExpect(status().isOk());

        // assert
        verify(eventProducerService).publishChatReaction(chatReaction);
    }

    @Test
    @DisplayName("success case : 첨부파일 ID 가 있는 메시지도 그대로 발행한다")
    void 첨부파일_ID_가_있는_메시지도_그대로_발행한다() throws Exception {
        // arrange
        ChatMessage chatMessage = ChatMessage.newBuilder()
            .setSendUserId(5L).setChannelId(7L).setMessage("").addAttachmentFileIds("6acc66675f99715e286f0f6e")
            .setPublishDt(1_700_000_000_000L).build();

        // act
        mockMvc.perform(post("/v1/private/event/publish/chat-send-message")
                .contentType("application/x-protobuf").content(chatMessage.toByteArray()))
            .andExpect(status().isOk());

        // assert
        verify(eventProducerService).publishChatSendMessage(chatMessage);
    }
}
