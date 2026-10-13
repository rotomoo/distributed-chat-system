package com.distributed.chat.system.client.api.web.dto.channel;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Schema(name = "읽음 처리 요청")
public class ReadChannelRequestDto {

    @Schema(name = "messageId", description = "여기까지 읽음 (MongoDB ObjectId)", example = "6acc66675f99715e286f0f6e")
    private String messageId;
}
