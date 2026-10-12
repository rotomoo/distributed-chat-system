package com.distributed.chat.system.client.api.web.dto.channel;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Schema(name = "DM 열기 요청")
public class OpenDmRequestDto {

    @NotNull
    @Schema(name = "userId", description = "상대방 회원 ID", example = "2")
    private Long userId;
}
