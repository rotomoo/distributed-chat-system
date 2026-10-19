package com.distributed.chat.system.message.consumer.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.distributed.chat.system.common.event.ChatMessageEvent;
import com.distributed.chat.system.mongodb.collection.ChatMessage.Attachment;
import com.distributed.chat.system.mongodb.file.ChatFile;
import com.distributed.chat.system.mongodb.file.ChatFileStore;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@DisplayName("[단위] ChatAttachmentResolver 테스트")
@ExtendWith(MockitoExtension.class)
class ChatAttachmentResolverUnitTest {

    private static final Long SENDER = 5L;
    private static final Long CHANNEL = 7L;
    private static final String MINE = "000000000000000000000001";
    private static final String MINE_2 = "000000000000000000000002";
    private static final String OTHER_CHANNEL = "000000000000000000000003";
    private static final String OTHER_UPLOADER = "000000000000000000000004";
    private static final String MISSING = "000000000000000000000005";

    @Mock
    private ChatFileStore chatFileStore;

    @InjectMocks
    private ChatAttachmentResolver chatAttachmentResolver;

    @Test
    @DisplayName("success case : 이 채널에 보낸 사람이 올린 파일만 GridFS 의 이름/형식/크기로 보낸 순서대로 남긴다")
    void 이_채널에_보낸_사람이_올린_파일만_GridFS_의_이름_형식_크기로_보낸_순서대로_남긴다() {
        // arrange
        ChatMessageEvent chatMessage = message(MINE_2, OTHER_CHANNEL, MINE, OTHER_UPLOADER, MISSING, MINE);
        when(chatFileStore.findAllById(List.of(MINE_2, OTHER_CHANNEL, MINE, OTHER_UPLOADER, MISSING))).thenReturn(List.of(
            new ChatFile(MINE, CHANNEL, SENDER, "image/png", "사진.png", 10L),
            new ChatFile(OTHER_CHANNEL, 8L, SENDER, "image/png", "다른채널.png", 11L),
            new ChatFile(OTHER_UPLOADER, CHANNEL, 9L, "image/png", "남의것.png", 12L),
            new ChatFile(MINE_2, CHANNEL, SENDER, "application/pdf", "회의록.pdf", 13L)));

        // act
        List<Attachment> attachments = chatAttachmentResolver.resolve(chatMessage);

        // assert
        assertThat(attachments).containsExactly(
            new Attachment(MINE_2, "회의록.pdf", "application/pdf", 13L),
            new Attachment(MINE, "사진.png", "image/png", 10L));
    }

    @Test
    @DisplayName("success case : 첨부가 없으면 GridFS 를 보지 않는다")
    void 첨부가_없으면_GridFS_를_보지_않는다() {
        // act
        List<Attachment> attachments = chatAttachmentResolver.resolve(message());

        // assert
        assertThat(attachments).isEmpty();
        verifyNoInteractions(chatFileStore);
    }

    private ChatMessageEvent message(String... fileIds) {
        return new ChatMessageEvent("event-1", SENDER, CHANNEL, "", 1L, List.of(fileIds));
    }
}
