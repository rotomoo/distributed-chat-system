package com.distributed.chat.system.client.api.web.dto.channel;

import com.fasterxml.jackson.annotation.JsonInclude;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
@AllArgsConstructor
@Schema(name = "채널")
public class ChannelDto {

    @Schema(name = "channelId", description = "채널 ID", example = "1")
    private Long channelId;

    @Schema(name = "channelName", description = "채널 이름", example = "general")
    private String channelName;

    @Schema(name = "joined", description = "내가 참여한 채널인지", example = "true")
    private Boolean joined;

    /**
     * 채널 목록에서만 채운다 (생성/참여 응답에는 없음)
     */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    @Schema(name = "unreadCount", description = "안 읽은 메시지 수 (참여 안 한 채널은 0, 최대 100)", example = "3")
    private Integer unreadCount;

    public ChannelDto(Long channelId, String channelName, Boolean joined) {
        this(channelId, channelName, joined, null);
    }
}
