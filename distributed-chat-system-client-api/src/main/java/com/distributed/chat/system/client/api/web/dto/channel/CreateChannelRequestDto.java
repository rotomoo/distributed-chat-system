package com.distributed.chat.system.client.api.web.dto.channel;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Schema(name = "채널 생성 요청")
public class CreateChannelRequestDto {

    @NotBlank
    @Size(max = 50)
    @Schema(name = "channelName", description = "채널 이름", example = "random")
    private String channelName;
}
