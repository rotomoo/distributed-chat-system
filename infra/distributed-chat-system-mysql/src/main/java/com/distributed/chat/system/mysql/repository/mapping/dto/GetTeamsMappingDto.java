package com.distributed.chat.system.mysql.repository.mapping.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;
import lombok.Getter;

@Getter
public class GetTeamsMappingDto {

    @Schema(name = "teamId", title = "팀 ID")
    private Long teamId;

    @Schema(name = "teamName", title = "팀 이름")
    private String teamName;

    @Schema(name = "deleteYn", title = "삭제 여부")
    private Boolean deleteYn;

    @Schema(name = "createDt", title = "생성 일시")
    private Instant createDt;

    @Schema(name = "updateDt", title = "수정 일시")
    private Instant updateDt;

    @Schema(name = "createUserId", title = "생성 유저 ID")
    private Long createUserId;

    @Schema(name = "updateUserId", title = "수정 유저 ID")
    private Long updateUserId;
}
