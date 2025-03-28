package com.distributed.chat.system.mysql.repository.mapping.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Getter;

@Getter
public class GetTeamsMappingDto {

    @Schema(name = "teamId", title = "팀 ID")
    private Long teamId;

    @Schema(name = "teamName", title = "팀 이름")
    private String teamName;

    @Schema(name = "deleteYn", title = "삭제 여부")
    private Boolean deleteYn;
}
