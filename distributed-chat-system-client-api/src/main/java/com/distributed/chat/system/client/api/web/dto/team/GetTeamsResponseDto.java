package com.distributed.chat.system.client.api.web.dto.team;

import com.distributed.chat.system.client.api.web.dto.common.PagingInfo;
import com.distributed.chat.system.mysql.repository.mapping.dto.GetTeamsMappingDto;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class GetTeamsResponseDto {

    @Schema(name = "paging", title = "페이징 정보")
    private PagingInfo paging;

    @Schema(name = "list", title = "팀 목록")
    private List<GetTeamsMappingDto> list;
}
