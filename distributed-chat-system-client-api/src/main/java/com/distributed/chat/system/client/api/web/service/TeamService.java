package com.distributed.chat.system.client.api.web.service;

import com.distributed.chat.system.client.api.web.dto.common.PagingInfo;
import com.distributed.chat.system.client.api.web.dto.team.CreateTeamRequestDto;
import com.distributed.chat.system.client.api.web.dto.team.GetTeamsRequestDto;
import com.distributed.chat.system.client.api.web.dto.team.GetTeamsResponseDto;
import com.distributed.chat.system.common.exception.ApiException;
import com.distributed.chat.system.common.exception.ErrorCode;
import com.distributed.chat.system.common.response.ResponseData;
import com.distributed.chat.system.mysql.entity.Team;
import com.distributed.chat.system.mysql.repository.TeamRepository;
import com.distributed.chat.system.mysql.repository.mapping.dto.GetTeamsMappingDto;
import java.util.Map;
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

    /**
     * 팀 등록
     *
     * @param createTeamRequestDto
     * @return
     */
    public ResponseData createTeam(CreateTeamRequestDto createTeamRequestDto) {
        if (teamRepository.existsByTeamNameAndDeleteYnIsFalse(createTeamRequestDto.getTeamName())) {
            throw new ApiException(ErrorCode.ALREADY_USING_TEAM_NAME);
        }

        Team team = saveTeam(createTeamRequestDto);

        return ResponseData.success("팀 등록", Map.of("team", team));
    }

    public Team saveTeam(CreateTeamRequestDto createTeamRequestDto) {
        return teamRepository.save(createTeamEntity(createTeamRequestDto));
    }

    public Team createTeamEntity(CreateTeamRequestDto createTeamRequestDto) {
        return Team.builder()
            .teamName(createTeamRequestDto.getTeamName())
            .build();
    }
}
