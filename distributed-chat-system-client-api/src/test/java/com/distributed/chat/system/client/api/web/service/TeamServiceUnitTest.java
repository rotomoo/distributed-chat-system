package com.distributed.chat.system.client.api.web.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowableOfType;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.distributed.chat.system.client.api.web.dto.team.CreateTeamRequestDto;
import com.distributed.chat.system.common.exception.ApiException;
import com.distributed.chat.system.common.exception.ErrorCode;
import com.distributed.chat.system.common.response.ResponseData;
import com.distributed.chat.system.mysql.entity.Team;
import com.distributed.chat.system.mysql.repository.TeamRepository;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;

@DisplayName("[단위] TeamService 테스트")
@ExtendWith(MockitoExtension.class)
public class TeamServiceUnitTest {

    @Mock
    private TeamRepository teamRepository;

    @InjectMocks
    private TeamService teamService;

    private CreateTeamRequestDto testDto;

    @BeforeEach
    void setup() {
        testDto = new CreateTeamRequestDto("FC서울");
    }

    @Test
    @DisplayName("true case: 팀 등록 성공")
    void createTeamTrue() {
        // arrange
        when(teamRepository.existsByTeamNameAndDeleteYnIsFalse(testDto.getTeamName())).thenReturn(false);
        when(teamRepository.save(any(Team.class)))
            .thenReturn(new Team(1L, "FC서울", false));

        // act
        ResponseData<Map<String, Team>> response = teamService.createTeam(testDto);

        // assert
        assertThat(response.isSuccess()).isTrue();
        assertThat(response.getCode()).isEqualTo(0);
        assertThat(response.getMsg()).isEqualTo("팀 등록");

        Team team = response.getData().get("team");
        assertThat(team.getTeamName()).isEqualTo("FC서울");
        assertThat(team.getDeleteYn()).isEqualTo(false);
        verify(teamRepository, times(1)).save(any(Team.class));
    }

    @Test
    @DisplayName("false case: 이미 생성된 팀 이름 등록 실패")
    void createTeamFalse() {
        // arrange
        when(teamRepository.existsByTeamNameAndDeleteYnIsFalse("FC서울")).thenReturn(true);

        // act
        ApiException exception = catchThrowableOfType(() -> teamService.createTeam(testDto),
            ApiException.class);

        // assert
        assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.ALREADY_USING_TEAM_NAME);
        assertThat(exception.getErrorCode().getCustomCode()).isEqualTo(2001);
        assertThat(exception.getErrorCode().getHttpStatus()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(exception.getErrorCode().getMsg()).isEqualTo("이미 존재하는 팀입니다.");
    }
}
