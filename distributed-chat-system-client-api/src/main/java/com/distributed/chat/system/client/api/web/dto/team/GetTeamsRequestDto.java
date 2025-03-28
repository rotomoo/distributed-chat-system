package com.distributed.chat.system.client.api.web.dto.team;

import com.distributed.chat.system.client.api.web.dto.common.PageRequestDto;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Schema(name = "팀 목록 조회 요청")
public class GetTeamsRequestDto extends PageRequestDto {

    /* 요청 필터 */
    @Schema(name = "teamId", description = "팀 ID", example = "1")
    private Long teamId;

    @Schema(name = "teamName", description = "팀 이름", example = "FC서울")
    private String teamName;

    @Schema(name = "deleteYn", description = "삭제 여부", example = "false")
    private Boolean deleteYn;

    /* 정렬 */
    @Schema(name = "sort", description = "정렬 기준. 기본값 ID_DESC. 사용 가능한 값: ID_DESC, ID_ASC", example = "ID_DESC")
    private TeamsSort sort = TeamsSort.ID_DESC;
}
