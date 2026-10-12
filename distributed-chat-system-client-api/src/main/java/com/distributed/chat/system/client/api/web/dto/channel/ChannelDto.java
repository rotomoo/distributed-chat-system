package com.distributed.chat.system.client.api.web.dto.channel;

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
}
