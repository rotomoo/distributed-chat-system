package com.distributed.chat.system.client.api.web.dto.channel;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
@AllArgsConstructor
@Schema(name = "안 읽은 메시지 따라잡기")
public class CatchUpDto {

    @Schema(name = "unreadCount", description = "안 읽은 메시지 수 (내가 보낸 것 제외, 상한 없음)", example = "12")
    private Integer unreadCount;

    @Schema(name = "firstUnreadMessageId", description = "안 읽은 첫 메시지 ID (구분선 위치). 없으면 null", example = "6acc66675f99715e286f0f6e")
    private String firstUnreadMessageId;
}
