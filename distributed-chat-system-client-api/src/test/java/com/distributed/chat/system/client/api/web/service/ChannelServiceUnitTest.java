package com.distributed.chat.system.client.api.web.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowableOfType;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.distributed.chat.system.client.api.base.cache.ChannelMemberCache;
import com.distributed.chat.system.client.api.web.dto.channel.ChannelDto;
import com.distributed.chat.system.client.api.web.dto.channel.CreateChannelRequestDto;
import com.distributed.chat.system.client.api.web.dto.channel.DmDto;
import com.distributed.chat.system.client.api.web.dto.channel.OpenDmRequestDto;
import com.distributed.chat.system.common.exception.ApiException;
import com.distributed.chat.system.common.exception.ErrorCode;
import com.distributed.chat.system.common.response.ResponseData;
import com.distributed.chat.system.mysql.entity.Channel;
import com.distributed.chat.system.mysql.entity.ChannelUser;
import com.distributed.chat.system.mysql.entity.User;
import com.distributed.chat.system.mysql.enums.ChannelType;
import com.distributed.chat.system.mysql.repository.ChannelRepository;
import com.distributed.chat.system.mysql.repository.ChannelUserRepository;
import com.distributed.chat.system.mysql.repository.TeamUserRepository;
import com.distributed.chat.system.mysql.repository.UserRepository;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

@DisplayName("[단위] ChannelService 테스트")
@ExtendWith(MockitoExtension.class)
class ChannelServiceUnitTest {

    private static final Long LOGIN_ID = 3L;
    private static final Long TEAM_ID = 10L;

    @Mock
    private ChannelRepository channelRepository;

    @Mock
    private ChannelUserRepository channelUserRepository;

    @Mock
    private TeamUserRepository teamUserRepository;

    @Mock
    private UserRepository userRepository;

    @Mock
    private ChannelMemberCache channelMemberCache;

    @Mock
    private ChannelMessageService channelMessageService;

    @InjectMocks
    private ChannelService channelService;

    @BeforeEach
    void setup() {
        SecurityContextHolder.getContext().setAuthentication(
            new UsernamePasswordAuthenticationToken(String.valueOf(LOGIN_ID), null, List.of()));
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    @DisplayName("success case : 채널 목록에 내가 참여한 채널인지 표시한다")
    void 채널_목록에_내가_참여한_채널인지_표시한다() {
        // arrange
        givenTeamMember(LOGIN_ID);
        when(channelUserRepository.findAllByUserIdAndDeleteYnIsFalse(LOGIN_ID)).thenReturn(List.of(membership(100L, null)));
        when(channelRepository.findAllByTeamIdAndChannelTypeAndDeleteYnIsFalseOrderByIdAsc(TEAM_ID, ChannelType.PUBLIC))
            .thenReturn(List.of(publicChannel(100L, "general"), publicChannel(101L, "random")));

        // act
        ResponseData<Map<String, List<ChannelDto>>> response = channelService.getChannels(TEAM_ID);

        // assert
        List<ChannelDto> channels = response.getData().get("list");
        assertThat(channels).extracting(ChannelDto::getChannelName).containsExactly("general", "random");
        assertThat(channels).extracting(ChannelDto::getJoined).containsExactly(true, false);
    }

    @Test
    @DisplayName("success case : 채널 목록의 안 읽은 수는 참여한 채널만 내 읽음 위치로 100 까지 세고 나머지는 0 이다")
    void 채널_목록의_안_읽은_수는_참여한_채널만_내_읽음_위치로_100_까지_세고_나머지는_0_이다() {
        // arrange
        givenTeamMember(LOGIN_ID);
        ChannelUser general = membership(100L, "000000000000000000000002");
        when(channelUserRepository.findAllByUserIdAndDeleteYnIsFalse(LOGIN_ID)).thenReturn(List.of(general));
        when(channelRepository.findAllByTeamIdAndChannelTypeAndDeleteYnIsFalseOrderByIdAsc(TEAM_ID, ChannelType.PUBLIC))
            .thenReturn(List.of(publicChannel(100L, "general"), publicChannel(101L, "random")));
        when(channelMessageService.countUnread(List.of(general), LOGIN_ID, 100)).thenReturn(Map.of(100L, 7));

        // act
        ResponseData<Map<String, List<ChannelDto>>> response = channelService.getChannels(TEAM_ID);

        // assert
        assertThat(response.getData().get("list")).extracting(ChannelDto::getUnreadCount).containsExactly(7, 0);
        verify(channelMessageService, times(1)).countUnread(any(), any(), anyInt());
    }

    @Test
    @DisplayName("success case : DM 목록에 상대방과 안 읽은 수를 함께 준다")
    void DM_목록에_상대방과_안_읽은_수를_함께_준다() {
        // arrange
        givenTeamMember(LOGIN_ID);
        ChannelUser dm = membership(200L, null);
        when(channelUserRepository.findAllByUserIdAndDeleteYnIsFalse(LOGIN_ID)).thenReturn(List.of(dm, membership(100L, null)));
        when(channelRepository.findAllByIdInAndTeamIdAndChannelTypeAndDeleteYnIsFalse(Set.of(200L, 100L), TEAM_ID, ChannelType.DM))
            .thenReturn(List.of(new Channel(200L, TEAM_ID, "dm:3:7", ChannelType.DM, false)));
        when(userRepository.findAllById(any())).thenReturn(List.of(new User(7L, "bob", "bob", "hash", false)));
        when(channelMessageService.countUnread(List.of(dm), LOGIN_ID, 100)).thenReturn(Map.of(200L, 2));

        // act
        ResponseData<Map<String, List<DmDto>>> response = channelService.getDms(TEAM_ID);

        // assert
        DmDto found = response.getData().get("list").get(0);
        assertThat(found.getUserId()).isEqualTo(7L);
        assertThat(found.getUserName()).isEqualTo("bob");
        assertThat(found.getUnreadCount()).isEqualTo(2);
    }

    @Test
    @DisplayName("success case : 채널을 만들면 만든 사람이 참여하고 Redis 멤버 캐시에도 들어간다")
    void 채널을_만들면_만든_사람이_참여하고_Redis_멤버_캐시에도_들어간다() {
        // arrange
        givenTeamMember(LOGIN_ID);
        when(channelRepository.existsByTeamIdAndChannelTypeAndChannelNameAndDeleteYnIsFalse(TEAM_ID, ChannelType.PUBLIC, "random"))
            .thenReturn(false);
        when(channelRepository.save(any(Channel.class))).thenReturn(publicChannel(101L, "random"));

        // act
        channelService.createChannel(TEAM_ID, new CreateChannelRequestDto("  random  "));

        // assert
        ArgumentCaptor<Channel> saved = ArgumentCaptor.forClass(Channel.class);
        verify(channelRepository).save(saved.capture());
        assertThat(saved.getValue().getChannelName()).isEqualTo("random");
        assertThat(saved.getValue().getChannelType()).isEqualTo(ChannelType.PUBLIC);
        verify(channelUserRepository).save(any(ChannelUser.class));
        verify(channelMemberCache).add(101L, LOGIN_ID);
    }

    @Test
    @DisplayName("fail case : 같은 팀에 같은 이름의 채널은 만들 수 없다")
    void 같은_팀에_같은_이름의_채널은_만들_수_없다() {
        // arrange
        givenTeamMember(LOGIN_ID);
        when(channelRepository.existsByTeamIdAndChannelTypeAndChannelNameAndDeleteYnIsFalse(TEAM_ID, ChannelType.PUBLIC, "random"))
            .thenReturn(true);

        // act
        ApiException exception = catchThrowableOfType(
            () -> channelService.createChannel(TEAM_ID, new CreateChannelRequestDto("random")), ApiException.class);

        // assert
        assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.ALREADY_USING_CHANNEL_NAME);
        verify(channelRepository, never()).save(any(Channel.class));
    }

    @Test
    @DisplayName("fail case : 팀 멤버가 아니면 채널을 만들 수 없다")
    void 팀_멤버가_아니면_채널을_만들_수_없다() {
        // arrange
        when(teamUserRepository.existsByTeamIdAndUserIdAndDeleteYnIsFalse(TEAM_ID, LOGIN_ID)).thenReturn(false);

        // act
        ApiException exception = catchThrowableOfType(
            () -> channelService.createChannel(TEAM_ID, new CreateChannelRequestDto("random")), ApiException.class);

        // assert
        assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.NOT_TEAM_MEMBER);
    }

    @Test
    @DisplayName("success case : 이미 참여한 채널에 다시 참여해도 중복 저장하지 않는다")
    void 이미_참여한_채널에_다시_참여해도_중복_저장하지_않는다() {
        // arrange
        when(channelRepository.findByIdAndDeleteYnIsFalse(101L)).thenReturn(Optional.of(publicChannel(101L, "random")));
        givenTeamMember(LOGIN_ID);
        when(channelUserRepository.existsByChannelIdAndUserIdAndDeleteYnIsFalse(101L, LOGIN_ID)).thenReturn(true);

        // act
        channelService.joinChannel(101L);

        // assert
        verify(channelUserRepository, never()).save(any(ChannelUser.class));
        verify(channelMemberCache).add(101L, LOGIN_ID);
    }

    @Test
    @DisplayName("fail case : DM 채널은 참여 API 로 들어갈 수 없다")
    void DM_채널은_참여_API_로_들어갈_수_없다() {
        // arrange
        when(channelRepository.findByIdAndDeleteYnIsFalse(200L))
            .thenReturn(Optional.of(new Channel(200L, TEAM_ID, "dm:1:2", ChannelType.DM, false)));

        // act
        ApiException exception = catchThrowableOfType(() -> channelService.joinChannel(200L), ApiException.class);

        // assert
        assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.NOT_EXIST_CHANNEL);
        verify(channelUserRepository, never()).save(any(ChannelUser.class));
    }

    @Test
    @DisplayName("success case : 처음 여는 DM 은 두 사람을 멤버로 하는 채널을 만든다")
    void 처음_여는_DM_은_두_사람을_멤버로_하는_채널을_만든다() {
        // arrange
        givenTeamMember(LOGIN_ID);
        givenTeamMember(7L);
        when(channelRepository.findByTeamIdAndChannelTypeAndChannelNameAndDeleteYnIsFalse(TEAM_ID, ChannelType.DM, "dm:3:7"))
            .thenReturn(Optional.empty());
        when(channelRepository.save(any(Channel.class))).thenReturn(new Channel(200L, TEAM_ID, "dm:3:7", ChannelType.DM, false));
        when(userRepository.findById(7L)).thenReturn(Optional.of(new User(7L, "bob", "bob", "hash", false)));

        // act
        ResponseData<DmDto> response = channelService.openDm(TEAM_ID, new OpenDmRequestDto(7L));

        // assert
        assertThat(response.getData().getChannelId()).isEqualTo(200L);
        assertThat(response.getData().getUserName()).isEqualTo("bob");
        verify(channelUserRepository, times(2)).save(any(ChannelUser.class));
        verify(channelMemberCache).add(200L, LOGIN_ID);
        verify(channelMemberCache).add(200L, 7L);
    }

    @Test
    @DisplayName("success case : 이미 있는 DM 을 다시 열면 같은 채널을 쓴다")
    void 이미_있는_DM_을_다시_열면_같은_채널을_쓴다() {
        // arrange
        givenTeamMember(LOGIN_ID);
        givenTeamMember(7L);
        when(channelRepository.findByTeamIdAndChannelTypeAndChannelNameAndDeleteYnIsFalse(TEAM_ID, ChannelType.DM, "dm:3:7"))
            .thenReturn(Optional.of(new Channel(200L, TEAM_ID, "dm:3:7", ChannelType.DM, false)));
        when(channelUserRepository.existsByChannelIdAndUserIdAndDeleteYnIsFalse(any(), any())).thenReturn(true);

        // act
        ResponseData<DmDto> response = channelService.openDm(TEAM_ID, new OpenDmRequestDto(7L));

        // assert
        assertThat(response.getData().getChannelId()).isEqualTo(200L);
        verify(channelRepository, never()).save(any(Channel.class));
        verify(channelUserRepository, never()).save(any(ChannelUser.class));
    }

    @Test
    @DisplayName("fail case : 자기 자신과는 DM 을 열 수 없다")
    void 자기_자신과는_DM_을_열_수_없다() {
        // act
        ApiException exception = catchThrowableOfType(
            () -> channelService.openDm(TEAM_ID, new OpenDmRequestDto(LOGIN_ID)), ApiException.class);

        // assert
        assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.CANNOT_DM_SELF);
    }

    @Test
    @DisplayName("fail case : 같은 팀 멤버가 아니면 DM 을 열 수 없다")
    void 같은_팀_멤버가_아니면_DM_을_열_수_없다() {
        // arrange
        givenTeamMember(LOGIN_ID);
        when(teamUserRepository.existsByTeamIdAndUserIdAndDeleteYnIsFalse(TEAM_ID, 9L)).thenReturn(false);

        // act
        ApiException exception = catchThrowableOfType(
            () -> channelService.openDm(TEAM_ID, new OpenDmRequestDto(9L)), ApiException.class);

        // assert
        assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.NOT_TEAM_MEMBER);
        verify(channelRepository, never()).save(any(Channel.class));
    }

    @Test
    @DisplayName("success case : DM 채널 이름은 두 사람 순서와 상관없이 같다")
    void DM_채널_이름은_두_사람_순서와_상관없이_같다() {
        // act & assert
        assertThat(ChannelService.dmChannelName(3L, 7L)).isEqualTo("dm:3:7");
        assertThat(ChannelService.dmChannelName(7L, 3L)).isEqualTo("dm:3:7");
    }

    private ChannelUser membership(Long channelId, String lastReadMessageId) {
        return ChannelUser.builder().channelId(channelId).userId(LOGIN_ID).lastReadMessageId(lastReadMessageId).deleteYn(false).build();
    }

    private void givenTeamMember(Long userId) {
        when(teamUserRepository.existsByTeamIdAndUserIdAndDeleteYnIsFalse(TEAM_ID, userId)).thenReturn(true);
    }

    private Channel publicChannel(Long id, String name) {
        return new Channel(id, TEAM_ID, name, ChannelType.PUBLIC, false);
    }
}
