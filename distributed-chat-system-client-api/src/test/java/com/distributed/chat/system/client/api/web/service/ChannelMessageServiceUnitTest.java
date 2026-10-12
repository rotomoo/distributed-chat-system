package com.distributed.chat.system.client.api.web.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowableOfType;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.distributed.chat.system.client.api.web.dto.channel.ChannelMessageDto;
import com.distributed.chat.system.common.exception.ApiException;
import com.distributed.chat.system.common.exception.ErrorCode;
import com.distributed.chat.system.common.response.ResponseData;
import com.distributed.chat.system.mongodb.collection.ChatMessage;
import com.distributed.chat.system.mongodb.repository.ChatMessageRepository;
import com.distributed.chat.system.mysql.repository.ChannelUserRepository;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import org.bson.types.ObjectId;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageRequest;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

@DisplayName("[단위] ChannelMessageService 테스트")
@ExtendWith(MockitoExtension.class)
class ChannelMessageServiceUnitTest {

    private static final Long LOGIN_ID = 5L;

    @Mock
    private ChatMessageRepository chatMessageRepository;

    @Mock
    private ChannelUserRepository channelUserRepository;

    @InjectMocks
    private ChannelMessageService channelMessageService;

    @BeforeEach
    void setup() {
        SecurityContextHolder.getContext().setAuthentication(
            new UsernamePasswordAuthenticationToken(String.valueOf(LOGIN_ID), null, List.of()));
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    @DisplayName("success case : 최신 메시지를 가져와 오래된 순으로 돌려준다")
    void 최신_메시지를_가져와_오래된_순으로_돌려준다() {
        // arrange
        when(channelUserRepository.existsByChannelIdAndUserIdAndDeleteYnIsFalse(7L, LOGIN_ID)).thenReturn(true);
        when(chatMessageRepository.findByChannelIdOrderByIdDesc(7L, PageRequest.of(0, 2)))
            .thenReturn(List.of(message("000000000000000000000003", "세번째"), message("000000000000000000000002", "두번째")));

        // act
        ResponseData<Map<String, List<ChannelMessageDto>>> response = channelMessageService.getMessages(7L, null, 2);

        // assert
        assertThat(response.getData().get("list")).extracting(ChannelMessageDto::getMessage).containsExactly("두번째", "세번째");
    }

    @Test
    @DisplayName("success case : before 를 주면 그보다 이전 메시지를 가져온다")
    void before_를_주면_그보다_이전_메시지를_가져온다() {
        // arrange
        when(channelUserRepository.existsByChannelIdAndUserIdAndDeleteYnIsFalse(7L, LOGIN_ID)).thenReturn(true);
        when(chatMessageRepository.findByChannelIdAndIdLessThanOrderByIdDesc(eq(7L), eq(new ObjectId("000000000000000000000002")), any()))
            .thenReturn(List.of(message("000000000000000000000001", "첫번째")));

        // act
        ResponseData<Map<String, List<ChannelMessageDto>>> response =
            channelMessageService.getMessages(7L, "000000000000000000000002", 50);

        // assert
        assertThat(response.getData().get("list")).extracting(ChannelMessageDto::getMessage).containsExactly("첫번째");
    }

    @Test
    @DisplayName("fail case : 채널 멤버가 아니면 메시지를 볼 수 없다")
    void 채널_멤버가_아니면_메시지를_볼_수_없다() {
        // arrange
        when(channelUserRepository.existsByChannelIdAndUserIdAndDeleteYnIsFalse(anyLong(), anyLong())).thenReturn(false);

        // act
        ApiException exception = catchThrowableOfType(() -> channelMessageService.getMessages(7L, null, 50), ApiException.class);

        // assert
        assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.NOT_CHANNEL_MEMBER);
        verifyNoInteractions(chatMessageRepository);
    }

    @Test
    @DisplayName("fail case : before 가 메시지 ID 형식이 아니면 거절한다")
    void before_가_메시지_ID_형식이_아니면_거절한다() {
        // arrange
        when(channelUserRepository.existsByChannelIdAndUserIdAndDeleteYnIsFalse(7L, LOGIN_ID)).thenReturn(true);

        // act
        ApiException exception = catchThrowableOfType(() -> channelMessageService.getMessages(7L, "abc", 50), ApiException.class);

        // assert
        assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.INVALID_MESSAGE_CURSOR);
    }

    private ChatMessage message(String id, String content) {
        return ChatMessage.builder().id(id).channelId(7L).content(content).createUserId(LOGIN_ID)
            .eventPublishDt(Instant.ofEpochMilli(1_700_000_000_000L)).build();
    }
}
