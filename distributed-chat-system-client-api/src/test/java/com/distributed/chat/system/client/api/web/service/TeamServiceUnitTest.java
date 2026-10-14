package com.distributed.chat.system.client.api.web.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowableOfType;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.distributed.chat.system.client.api.web.dto.team.CreateTeamRequestDto;
import com.distributed.chat.system.client.api.web.dto.team.CreateTeamResponseDto;
import com.distributed.chat.system.client.api.web.dto.team.TeamMemberDto;
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
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

@DisplayName("[단위] TeamService 테스트")
@ExtendWith(MockitoExtension.class)
public class TeamServiceUnitTest {

    private static final Long LOGIN_ID = 1L;

    @Mock
    private TeamRepository teamRepository;

    @Mock
    private TeamUserRepository teamUserRepository;

    @Mock
    private ChannelRepository channelRepository;

    @Mock
    private UserRepository userRepository;

    @Mock
    private ChannelService channelService;

    @InjectMocks
    private TeamService teamService;

    private CreateTeamRequestDto testDto;

    @BeforeEach
    void setup() {
        testDto = new CreateTeamRequestDto("FC서울");
        SecurityContextHolder.getContext().setAuthentication(
            new UsernamePasswordAuthenticationToken(String.valueOf(LOGIN_ID), null, List.of()));
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    @DisplayName("success case : 팀을 만들면 만든 사람이 멤버가 되고 general 채널에 참여한다")
    void 팀을_만들면_만든_사람이_멤버가_되고_general_채널에_참여한다() {
        // arrange
        when(teamRepository.existsByTeamNameAndDeleteYnIsFalse("FC서울")).thenReturn(false);
        when(teamRepository.save(any(Team.class))).thenReturn(new Team(10L, "FC서울", false));
        when(channelService.saveChannel(10L, "general", ChannelType.PUBLIC))
            .thenReturn(new Channel(100L, 10L, "general", ChannelType.PUBLIC, false));

        // act
        ResponseData<CreateTeamResponseDto> response = teamService.createTeam(testDto);

        // assert
        assertThat(response.getMsg()).isEqualTo("팀 등록");
        assertThat(response.getData().getTeamId()).isEqualTo(10L);
        assertThat(response.getData().getDefaultChannelId()).isEqualTo(100L);
        ArgumentCaptor<TeamUser> teamUser = ArgumentCaptor.forClass(TeamUser.class);
        verify(teamUserRepository).save(teamUser.capture());
        assertThat(teamUser.getValue().getTeamId()).isEqualTo(10L);
        assertThat(teamUser.getValue().getUserId()).isEqualTo(LOGIN_ID);
        verify(channelService).join(100L, LOGIN_ID);
    }

    @Test
    @DisplayName("fail case : 이미 있는 팀 이름은 만들 수 없다")
    void 이미_있는_팀_이름은_만들_수_없다() {
        // arrange
        when(teamRepository.existsByTeamNameAndDeleteYnIsFalse("FC서울")).thenReturn(true);

        // act
        ApiException exception = catchThrowableOfType(() -> teamService.createTeam(testDto), ApiException.class);

        // assert
        assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.ALREADY_USING_TEAM_NAME);
        assertThat(exception.getErrorCode().getCustomCode()).isEqualTo(2001);
        assertThat(exception.getErrorCode().getHttpStatus()).isEqualTo(HttpStatus.BAD_REQUEST);
        verify(teamRepository, never()).save(any(Team.class));
    }

    @Test
    @DisplayName("success case : 팀에 가입하면 멤버가 되고 general 채널에 참여한다")
    void 팀에_가입하면_멤버가_되고_general_채널에_참여한다() {
        // arrange
        when(teamRepository.findByIdAndDeleteYnIsFalse(10L)).thenReturn(Optional.of(new Team(10L, "FC서울", false)));
        when(teamUserRepository.existsByTeamIdAndUserIdAndDeleteYnIsFalse(10L, LOGIN_ID)).thenReturn(false);
        when(channelRepository.findByTeamIdAndChannelTypeAndChannelNameAndDeleteYnIsFalse(10L, ChannelType.PUBLIC, "general"))
            .thenReturn(Optional.of(new Channel(100L, 10L, "general", ChannelType.PUBLIC, false)));

        // act
        teamService.joinTeam(10L);

        // assert
        verify(teamUserRepository, times(1)).save(any(TeamUser.class));
        verify(channelService).join(100L, LOGIN_ID);
    }

    @Test
    @DisplayName("success case : 이미 멤버인 팀에 다시 가입해도 중복 저장하지 않는다")
    void 이미_멤버인_팀에_다시_가입해도_중복_저장하지_않는다() {
        // arrange
        when(teamRepository.findByIdAndDeleteYnIsFalse(10L)).thenReturn(Optional.of(new Team(10L, "FC서울", false)));
        when(teamUserRepository.existsByTeamIdAndUserIdAndDeleteYnIsFalse(10L, LOGIN_ID)).thenReturn(true);

        // act
        teamService.joinTeam(10L);

        // assert
        verify(teamUserRepository, never()).save(any(TeamUser.class));
    }

    @Test
    @DisplayName("fail case : 없는 팀에는 가입할 수 없다")
    void 없는_팀에는_가입할_수_없다() {
        // arrange
        when(teamRepository.findByIdAndDeleteYnIsFalse(99L)).thenReturn(Optional.empty());

        // act
        ApiException exception = catchThrowableOfType(() -> teamService.joinTeam(99L), ApiException.class);

        // assert
        assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.NOT_EXIST_TEAM);
    }

    @Test
    @DisplayName("fail case : 팀 멤버가 아니면 멤버 목록을 볼 수 없다")
    void 팀_멤버가_아니면_멤버_목록을_볼_수_없다() {
        // arrange
        when(teamUserRepository.existsByTeamIdAndUserIdAndDeleteYnIsFalse(10L, LOGIN_ID)).thenReturn(false);

        // act
        ApiException exception = catchThrowableOfType(() -> teamService.getTeamMembers(10L), ApiException.class);

        // assert
        assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.NOT_TEAM_MEMBER);
        assertThat(exception.getErrorCode().getHttpStatus()).isEqualTo(HttpStatus.FORBIDDEN);
    }

    @Test
    @DisplayName("success case : 팀 멤버 목록은 userId 순으로 이름만 준다 (접속 상태는 connection-status 가 준다)")
    void 팀_멤버_목록은_userId_순으로_이름만_준다() {
        // arrange
        when(teamUserRepository.existsByTeamIdAndUserIdAndDeleteYnIsFalse(10L, LOGIN_ID)).thenReturn(true);
        when(teamUserRepository.findAllByTeamIdAndDeleteYnIsFalse(10L)).thenReturn(List.of(
            TeamUser.builder().teamId(10L).userId(1L).build(),
            TeamUser.builder().teamId(10L).userId(2L).build()));
        when(userRepository.findAllById(List.of(1L, 2L))).thenReturn(List.of(
            new User(2L, "bob", "bob", "hash", false),
            new User(1L, "alice", "alice", "hash", false)));

        // act
        ResponseData<Map<String, List<TeamMemberDto>>> response = teamService.getTeamMembers(10L);

        // assert
        List<TeamMemberDto> members = response.getData().get("list");
        assertThat(members).extracting(TeamMemberDto::getUserName).containsExactly("alice", "bob");
        assertThat(members).extracting(TeamMemberDto::getUserId).containsExactly(1L, 2L);
    }
}
