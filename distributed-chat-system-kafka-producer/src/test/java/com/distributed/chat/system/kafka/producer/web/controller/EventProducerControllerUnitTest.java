package com.distributed.chat.system.kafka.producer.web.controller;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.distributed.chat.system.common.event.ChatMessageEvent;
import com.distributed.chat.system.common.event.ChatNotificationEvent;
import com.distributed.chat.system.common.event.ChatReactionEvent;
import com.distributed.chat.system.common.event.EventJson;
import com.distributed.chat.system.common.event.MalformedEventException;
import com.distributed.chat.system.kafka.producer.web.service.EventProducerService;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

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
        // Spring 기본 메시지 컨버터 (본문은 JSON 문자열 그대로 받는다)
        mockMvc = MockMvcBuilders.standaloneSetup(eventProducerController).build();
    }

    @Test
    @DisplayName("success case : JSON 리액션 본문을 받은 그대로 발행한다")
    void JSON_리액션_본문을_받은_그대로_발행한다() throws Exception {
        // arrange
        String body = EventJson.write(new ChatReactionEvent(5L, 7L, "6acc66675f99715e286f0f6e", "🎉", ChatReactionEvent.Action.REMOVE,
            1_700_000_000_000L));

        // act
        mockMvc.perform(post("/v1/private/event/publish/chat-reaction")
                .contentType(MediaType.APPLICATION_JSON).content(body))
            .andExpect(status().isOk());

        // assert
        verify(eventProducerService).publishChatReaction(body);
    }

    @Test
    @DisplayName("success case : 첨부파일 ID 가 있는 메시지도 받은 JSON 그대로 발행한다")
    void 첨부파일_ID_가_있는_메시지도_받은_JSON_그대로_발행한다() throws Exception {
        // arrange
        String body = EventJson.write(new ChatMessageEvent("event-1", 5L, 7L, "", 1_700_000_000_000L, List.of("6acc66675f99715e286f0f6e")));

        // act
        mockMvc.perform(post("/v1/private/event/publish/chat-send-message")
                .contentType(MediaType.APPLICATION_JSON).content(body))
            .andExpect(status().isOk());

        // assert
        verify(eventProducerService).publishChatSendMessage(body);
    }

    @Test
    @DisplayName("success case : JSON 알림 본문을 받은 그대로 발행한다")
    void JSON_알림_본문을_받은_그대로_발행한다() throws Exception {
        // arrange
        String body = EventJson.write(new ChatNotificationEvent("6acc66675f99715e286f0f6e", 7L, 5L, "점심 뭐 먹을까요?", 0,
            1_700_000_000_000L, List.of(9L)));

        // act
        mockMvc.perform(post("/v1/private/event/publish/chat-notification")
                .contentType(MediaType.APPLICATION_JSON).content(body))
            .andExpect(status().isOk());

        // assert
        verify(eventProducerService).publishChatNotification(body);
    }

    @Test
    @DisplayName("fail case : 이벤트로 읽을 수 없는 본문은 발행하지 않고 400")
    void 이벤트로_읽을_수_없는_본문은_발행하지_않고_400() throws Exception {
        // arrange
        doThrow(new MalformedEventException("ChatMessageEvent : broken")).when(eventProducerService).publishChatSendMessage(anyString());

        // act & assert
        mockMvc.perform(post("/v1/private/event/publish/chat-send-message")
                .contentType(MediaType.APPLICATION_JSON).content("{\"channelId\":"))
            .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("fail case : JSON 이 아닌 형식(예전 바이너리 형식)으로 보내면 415 로 받지 않는다")
    void JSON_이_아닌_형식으로_보내면_415_로_받지_않는다() throws Exception {
        // act
        mockMvc.perform(post("/v1/private/event/publish/chat-send-message")
                .contentType("application/x-binary").content(new byte[]{0x08, 0x05}))
            .andExpect(status().isUnsupportedMediaType());

        // assert
        verifyNoInteractions(eventProducerService);
    }
}
