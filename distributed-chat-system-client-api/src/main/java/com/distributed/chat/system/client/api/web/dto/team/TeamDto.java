package com.distributed.chat.system.client.api.web.dto.team;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
@AllArgsConstructor
@Schema(name = "팀")
public class TeamDto {

    @Schema(name = "teamId", description = "팀 ID", example = "1")
    private Long teamId;

    @Schema(name = "teamName", description = "팀 이름", example = "FC서울")
    private String teamName;
}
