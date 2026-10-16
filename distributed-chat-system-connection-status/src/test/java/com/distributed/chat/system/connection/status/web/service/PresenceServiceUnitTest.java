package com.distributed.chat.system.connection.status.web.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowableOfType;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.distributed.chat.system.common.exception.ApiException;
import com.distributed.chat.system.common.exception.ErrorCode;
import com.distributed.chat.system.common.response.ResponseData;
import com.distributed.chat.system.connection.status.web.dto.presence.UserPresenceDto;
import com.distributed.chat.system.mysql.entity.TeamUser;
import com.distributed.chat.system.mysql.repository.TeamUserRepository;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

@DisplayName("[단위] PresenceService 테스트")
@ExtendWith(MockitoExtension.class)
class PresenceServiceUnitTest {

    private static final Long LOGIN_ID = 1L;
    private static final Long TEAM_ID = 10L;

    @Mock
    private TeamUserRepository teamUserRepository;

    @Mock
    private RedisTemplate<String, Object> redisTemplate;

    @Mock
    private ValueOperations<String, Object> valueOperations;

    private PresenceService presenceService;

    @BeforeEach
    void setup() {
        lenient().when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        presenceService = new PresenceService(teamUserRepository, redisTemplate);
        SecurityContextHolder.getContext().setAuthentication(
            new UsernamePasswordAuthenticationToken(String.valueOf(LOGIN_ID), null, List.of()));
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    @DisplayName("success case : 팀 멤버 전체를 MGET 한 번으로 읽어 status 를 주고, 키가 없거나 알아볼 수 없는 값은 offline 이다")
    void 팀_멤버_전체를_MGET_한_번으로_읽어_status_를_주고_키가_없거나_알아볼_수_없는_값은_offline_이다() {
        // arrange : JSON 직렬화 RedisTemplate 이 {server, status} 를 읽으면 Map 이 온다
        givenMembers(4L, 1L, 3L, 2L);
        when(valueOperations.multiGet(List.of("1", "2", "3", "4"))).thenReturn(Arrays.asList(
            Map.of("server", "ws://10.0.0.1:1111", "status", "online"),
            Map.of("server", "ws://10.0.0.1:1111", "status", "away"),
            null,
            Map.of("status", "online")));

        // act
        ResponseData<Map<String, List<UserPresenceDto>>> response = presenceService.getTeamPresence(TEAM_ID);

        // assert
        List<UserPresenceDto> list = response.getData().get("list");
        assertThat(list).extracting(UserPresenceDto::getUserId).containsExactly(1L, 2L, 3L, 4L);
        assertThat(list).extracting(UserPresenceDto::getStatus).containsExactly("online", "away", "offline", "offline");
        assertThat(response.getMsg()).isEqualTo("팀 접속 상태 조회");
        verify(valueOperations, times(1)).multiGet(anyCollection());
        verify(teamUserRepository, times(1)).findAllByTeamIdAndDeleteYnIsFalse(TEAM_ID);
    }

    @Test
    @DisplayName("success case : Redis 가 결과를 주지 않으면(null) 모두 offline 이다")
    void Redis_가_결과를_주지_않으면_모두_offline_이다() {
        // arrange
        givenMembers(1L, 2L);
        when(valueOperations.multiGet(List.of("1", "2"))).thenReturn(null);

        // act
        ResponseData<Map<String, List<UserPresenceDto>>> response = presenceService.getTeamPresence(TEAM_ID);

        // assert
        assertThat(response.getData().get("list")).extracting(UserPresenceDto::getStatus).containsExactly("offline", "offline");
    }

    @Test
    @DisplayName("success case : 예전 값(서버 주소 문자열)은 online 이다")
    void 예전_값은_online_이다() {
        // arrange
        givenMembers(1L, 2L);
        when(valueOperations.multiGet(List.of("1", "2"))).thenReturn(Arrays.asList("ws://10.0.0.1:1111", null));

        // act
        ResponseData<Map<String, List<UserPresenceDto>>> response = presenceService.getTeamPresence(TEAM_ID);

        // assert
        assertThat(response.getData().get("list")).extracting(UserPresenceDto::getStatus).containsExactly("online", "offline");
    }

    @Test
    @DisplayName("fail case : 로그인 사용자가 팀 멤버가 아니면 2003 이고 Redis 는 읽지 않는다")
    void 로그인_사용자가_팀_멤버가_아니면_2003_이고_Redis_는_읽지_않는다() {
        // arrange
        givenMembers(2L, 3L);

        // act
        ApiException exception = catchThrowableOfType(() -> presenceService.getTeamPresence(TEAM_ID), ApiException.class);

        // assert
        assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.NOT_TEAM_MEMBER);
        assertThat(exception.getErrorCode().getCustomCode()).isEqualTo(2003);
        assertThat(exception.getErrorCode().getHttpStatus()).isEqualTo(HttpStatus.FORBIDDEN);
        verify(valueOperations, never()).multiGet(anyCollection());
    }

    @Test
    @DisplayName("fail case : 없는 팀(멤버 없음)도 팀 멤버가 아니므로 2003 이다")
    void 없는_팀도_팀_멤버가_아니므로_2003_이다() {
        // arrange
        givenMembers();

        // act
        ApiException exception = catchThrowableOfType(() -> presenceService.getTeamPresence(TEAM_ID), ApiException.class);

        // assert
        assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.NOT_TEAM_MEMBER);
        verifyNoInteractions(redisTemplate);
    }

    @Test
    @DisplayName("fail case : 로그인 정보가 없으면 1003 이고 MySQL / Redis 를 읽지 않는다")
    void 로그인_정보가_없으면_1003_이고_MySQL_Redis_를_읽지_않는다() {
        // arrange
        SecurityContextHolder.clearContext();

        // act
        ApiException exception = catchThrowableOfType(() -> presenceService.getTeamPresence(TEAM_ID), ApiException.class);

        // assert
        assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.NOT_EXIST_USER_INFO);
        verifyNoInteractions(teamUserRepository, redisTemplate);
    }

    private void givenMembers(Long... userIds) {
        when(teamUserRepository.findAllByTeamIdAndDeleteYnIsFalse(TEAM_ID)).thenReturn(Arrays.stream(userIds)
            .map(userId -> TeamUser.builder().teamId(TEAM_ID).userId(userId).deleteYn(false).build())
            .toList());
    }
}
