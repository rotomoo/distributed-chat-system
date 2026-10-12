package com.distributed.chat.system.client.api.web.dto.channel;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
@AllArgsConstructor
@Schema(name = "다이렉트 메시지")
public class DmDto {

    @Schema(name = "channelId", description = "DM 채널 ID", example = "7")
    private Long channelId;

    @Schema(name = "userId", description = "상대방 회원 ID", example = "2")
    private Long userId;

    @Schema(name = "userName", description = "상대방 이름", example = "bob")
    private String userName;
}
