package com.distributed.chat.system.client.api.web.dto.channel;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
@AllArgsConstructor
@Schema(name = "채널 알림 설정 요청")
public class ChannelNotificationRequestDto {

    @NotNull
    @Schema(name = "muted", description = "true 면 이 채널의 웹 푸시 알림을 끈다", example = "true")
    private Boolean muted;
}
