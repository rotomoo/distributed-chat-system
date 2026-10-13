package com.distributed.chat.system.client.api.web.dto.channel;

import com.distributed.chat.system.client.api.web.dto.file.FileDto;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 웹소켓으로 전달되는 메시지와 같은 모양 (type 제외)
 */
@Getter
@NoArgsConstructor
@AllArgsConstructor
@Schema(name = "채널 메시지")
public class ChannelMessageDto {

    @Schema(name = "messageId", description = "메시지 ID (MongoDB ObjectId)", example = "6acc66675f99715e286f0f6e")
    private String messageId;

    @Schema(name = "channelId", description = "채널 ID", example = "1")
    private Long channelId;

    @Schema(name = "sendUserId", description = "보낸 사람 ID", example = "5")
    private Long sendUserId;

    @Schema(name = "message", description = "내용", example = "안녕하세요")
    private String message;

    @Schema(name = "publishDt", description = "보낸 시각 (epoch millis)", example = "1791780452569")
    private Long publishDt;

    @Schema(name = "attachments", description = "첨부파일 (없으면 [])")
    private List<FileDto> attachments;

    @Schema(name = "reactions", description = "이모지 리액션 (없으면 [])")
    private List<ReactionDto> reactions;
}
