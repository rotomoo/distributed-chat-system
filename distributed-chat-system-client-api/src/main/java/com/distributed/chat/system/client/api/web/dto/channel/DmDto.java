package com.distributed.chat.system.client.api.web.dto.channel;

import com.fasterxml.jackson.annotation.JsonInclude;
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

    /**
     * DM 목록에서만 채운다 (DM 열기 응답에는 없음)
     */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    @Schema(name = "unreadCount", description = "안 읽은 메시지 수 (최대 100)", example = "3")
    private Integer unreadCount;

    public DmDto(Long channelId, Long userId, String userName) {
        this(channelId, userId, userName, null);
    }
}
