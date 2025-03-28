package com.distributed.chat.system.client.api.web.service;

import com.distributed.chat.system.client.api.web.dto.common.PagingInfo;
import com.distributed.chat.system.client.api.web.dto.team.GetTeamsRequestDto;
import com.distributed.chat.system.client.api.web.dto.team.GetTeamsResponseDto;
import com.distributed.chat.system.common.response.ResponseData;
import com.distributed.chat.system.mysql.repository.TeamRepository;
import com.distributed.chat.system.mysql.repository.mapping.dto.GetTeamsMappingDto;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
@Slf4j
public class TeamService {

    private final TeamRepository teamRepository;

    /**
     * 팀 목록 조회
     *
     * @param getTeamsRequestDto
     * @return
     */
    public ResponseData getTeams(GetTeamsRequestDto getTeamsRequestDto) {
        Page<GetTeamsMappingDto> getTeamsMappingDtoPage = teamRepository.findTeamsQueryDsl(
            getTeamsRequestDto.toFilterMap(),
            getTeamsRequestDto.toPageable(getTeamsRequestDto.getSort()));

        return ResponseData.success("팀 목록 조회", new GetTeamsResponseDto(
            PagingInfo.createPagingInfo(getTeamsMappingDtoPage),
            getTeamsMappingDtoPage.getContent()));
    }
}
