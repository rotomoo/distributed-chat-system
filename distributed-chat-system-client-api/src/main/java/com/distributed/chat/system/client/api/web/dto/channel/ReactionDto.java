package com.distributed.chat.system.client.api.web.dto.channel;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
@AllArgsConstructor
@Schema(name = "이모지 리액션")
public class ReactionDto {

    @Schema(name = "emoji", description = "이모지", example = "👍")
    private String emoji;

    @Schema(name = "userIds", description = "누른 사람 (개수 = 길이)", example = "[1, 5]")
    private List<Long> userIds;
}
