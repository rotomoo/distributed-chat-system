package com.distributed.chat.system.client.api.web.service;

import com.distributed.chat.system.client.api.base.presence.UserPresenceReader;
import com.distributed.chat.system.client.api.base.security.SecurityUtil;
import com.distributed.chat.system.client.api.web.dto.common.PagingInfo;
import com.distributed.chat.system.client.api.web.dto.team.CreateTeamResponseDto;
import com.distributed.chat.system.client.api.web.dto.team.TeamDto;
import com.distributed.chat.system.client.api.web.dto.team.TeamMemberDto;
import com.distributed.chat.system.client.api.web.dto.team.CreateTeamRequestDto;
import com.distributed.chat.system.client.api.web.dto.team.GetTeamsRequestDto;
import com.distributed.chat.system.client.api.web.dto.team.GetTeamsResponseDto;
import com.distributed.chat.system.common.exception.ApiException;
import com.distributed.chat.system.common.exception.ErrorCode;
import com.distributed.chat.system.common.response.ResponseData;
import com.distributed.chat.system.mysql.entity.Channel;
import com.distributed.chat.system.mysql.entity.Team;
import com.distributed.chat.system.mysql.entity.TeamUser;
import com.distributed.chat.system.mysql.entity.User;
import com.distributed.chat.system.mysql.enums.ChannelType;
import com.distributed.chat.system.mysql.repository.ChannelRepository;
import com.distributed.chat.system.mysql.repository.TeamRepository;
import com.distributed.chat.system.mysql.repository.TeamUserRepository;
import com.distributed.chat.system.mysql.repository.UserRepository;
import java.util.Comparator;
import java.util.List;
import com.distributed.chat.system.mysql.repository.mapping.dto.GetTeamsMappingDto;
import java.util.Map;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Slf4j
public class TeamService {

    private final TeamRepository teamRepository;
    private final TeamUserRepository teamUserRepository;
    private final ChannelRepository channelRepository;
    private final UserRepository userRepository;
    private final ChannelService channelService;
    private final UserPresenceReader userPresenceReader;

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
     * 팀(워크스페이스) 등록. 만든 사람을 멤버로 넣고 #general 채널을 함께 만든다
     *
     * @param createTeamRequestDto
     * @return
     */
    @Transactional
    public ResponseData createTeam(CreateTeamRequestDto createTeamRequestDto) {
        Long loginId = SecurityUtil.getLoginId();
        if (teamRepository.existsByTeamNameAndDeleteYnIsFalse(createTeamRequestDto.getTeamName())) {
            throw new ApiException(ErrorCode.ALREADY_USING_TEAM_NAME);
        }

        Team team = saveTeam(createTeamRequestDto);
        addTeamMember(team.getId(), loginId);
        Channel general = channelService.saveChannel(team.getId(), ChannelService.DEFAULT_CHANNEL_NAME, ChannelType.PUBLIC);
        channelService.join(general.getId(), loginId);

        return ResponseData.success("팀 등록", new CreateTeamResponseDto(team.getId(), team.getTeamName(), general.getId()));
    }

    public Team saveTeam(CreateTeamRequestDto createTeamRequestDto) {
        return teamRepository.save(createTeamEntity(createTeamRequestDto));
    }

    public Team createTeamEntity(CreateTeamRequestDto createTeamRequestDto) {
        return Team.builder()
            .teamName(createTeamRequestDto.getTeamName())
            .build();
    }

    /**
     * 내가 속한 팀 목록
     */
    public ResponseData getMyTeams() {
        Long loginId = SecurityUtil.getLoginId();

        List<Long> teamIds = teamUserRepository.findAllByUserIdAndDeleteYnIsFalse(loginId).stream()
            .map(TeamUser::getTeamId)
            .toList();
        List<TeamDto> teams = teamRepository.findAllByIdInAndDeleteYnIsFalseOrderByIdAsc(teamIds).stream()
            .map(team -> new TeamDto(team.getId(), team.getTeamName()))
            .toList();

        return ResponseData.success("내 팀 목록 조회", Map.of("list", teams));
    }

    /**
     * 팀 가입. #general 채널에도 함께 참여한다. 이미 멤버면 그대로 성공
     */
    @Transactional
    public ResponseData joinTeam(Long teamId) {
        Long loginId = SecurityUtil.getLoginId();
        Team team = teamRepository.findByIdAndDeleteYnIsFalse(teamId)
            .orElseThrow(() -> new ApiException(ErrorCode.NOT_EXIST_TEAM));

        addTeamMember(teamId, loginId);
        channelRepository.findByTeamIdAndChannelTypeAndChannelNameAndDeleteYnIsFalse(
                teamId, ChannelType.PUBLIC, ChannelService.DEFAULT_CHANNEL_NAME)
            .ifPresent(general -> channelService.join(general.getId(), loginId));

        return ResponseData.success("팀 가입", new TeamDto(team.getId(), team.getTeamName()));
    }

    /**
     * 팀 멤버 목록 (DM 상대 고르기). online = 지금 채팅 서버에 연결된 기기가 있는지
     */
    public ResponseData getTeamMembers(Long teamId) {
        Long loginId = SecurityUtil.getLoginId();
        if (!teamUserRepository.existsByTeamIdAndUserIdAndDeleteYnIsFalse(teamId, loginId)) {
            throw new ApiException(ErrorCode.NOT_TEAM_MEMBER);
        }

        List<Long> userIds = teamUserRepository.findAllByTeamIdAndDeleteYnIsFalse(teamId).stream()
            .map(TeamUser::getUserId)
            .toList();
        Set<Long> onlineUserIds = userPresenceReader.findOnlineUserIds(userIds);
        List<TeamMemberDto> members = userRepository.findAllById(userIds).stream()
            .sorted(Comparator.comparing(User::getId))
            .map(user -> new TeamMemberDto(user.getId(), user.getUserName(), onlineUserIds.contains(user.getId())))
            .toList();

        return ResponseData.success("팀 멤버 목록 조회", Map.of("list", members));
    }

    private void addTeamMember(Long teamId, Long userId) {
        if (!teamUserRepository.existsByTeamIdAndUserIdAndDeleteYnIsFalse(teamId, userId)) {
            teamUserRepository.save(TeamUser.builder()
                .teamId(teamId)
                .userId(userId)
                .build());
        }
    }
}
