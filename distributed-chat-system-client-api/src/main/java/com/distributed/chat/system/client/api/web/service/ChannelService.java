package com.distributed.chat.system.client.api.web.service;

import com.distributed.chat.system.client.api.base.cache.ChannelMemberCache;
import com.distributed.chat.system.client.api.base.security.SecurityUtil;
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
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 슬랙 기준 채널 : 워크스페이스(팀) 안의 공개 채널은 목록에서 보고 참여해야 메시지를 받는다. DM 은 두 사람만의 채널.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class ChannelService {

    public static final String DEFAULT_CHANNEL_NAME = "general";

    private final ChannelRepository channelRepository;
    private final ChannelUserRepository channelUserRepository;
    private final TeamUserRepository teamUserRepository;
    private final UserRepository userRepository;
    private final ChannelMemberCache channelMemberCache;

    /**
     * 팀의 공개 채널 목록 (참여 여부 포함)
     */
    public ResponseData getChannels(Long teamId) {
        Long loginId = SecurityUtil.getLoginId();
        requireTeamMember(teamId, loginId);

        Set<Long> joinedChannelIds = getJoinedChannelIds(loginId);
        List<ChannelDto> channels = channelRepository
            .findAllByTeamIdAndChannelTypeAndDeleteYnIsFalseOrderByIdAsc(teamId, ChannelType.PUBLIC).stream()
            .map(channel -> new ChannelDto(channel.getId(), channel.getChannelName(), joinedChannelIds.contains(channel.getId())))
            .toList();

        return ResponseData.success("채널 목록 조회", Map.of("list", channels));
    }

    /**
     * 공개 채널 생성. 만든 사람은 바로 참여한다
     */
    @Transactional
    public ResponseData createChannel(Long teamId, CreateChannelRequestDto createChannelRequestDto) {
        Long loginId = SecurityUtil.getLoginId();
        requireTeamMember(teamId, loginId);

        String channelName = createChannelRequestDto.getChannelName().trim();
        if (channelRepository.existsByTeamIdAndChannelTypeAndChannelNameAndDeleteYnIsFalse(teamId, ChannelType.PUBLIC, channelName)) {
            throw new ApiException(ErrorCode.ALREADY_USING_CHANNEL_NAME);
        }

        Channel channel = saveChannel(teamId, channelName, ChannelType.PUBLIC);
        join(channel.getId(), loginId);

        return ResponseData.success("채널 생성", new ChannelDto(channel.getId(), channel.getChannelName(), true));
    }

    /**
     * 공개 채널 참여. 이미 참여했으면 그대로 성공
     */
    @Transactional
    public ResponseData joinChannel(Long channelId) {
        Long loginId = SecurityUtil.getLoginId();

        Channel channel = channelRepository.findByIdAndDeleteYnIsFalse(channelId)
            .filter(found -> found.getChannelType() == ChannelType.PUBLIC)
            .orElseThrow(() -> new ApiException(ErrorCode.NOT_EXIST_CHANNEL));
        requireTeamMember(channel.getTeamId(), loginId);

        join(channelId, loginId);

        return ResponseData.success("채널 참여", new ChannelDto(channel.getId(), channel.getChannelName(), true));
    }

    /**
     * 내 DM 목록
     */
    public ResponseData getDms(Long teamId) {
        Long loginId = SecurityUtil.getLoginId();
        requireTeamMember(teamId, loginId);

        List<Channel> dmChannels = channelRepository.findAllByIdInAndTeamIdAndChannelTypeAndDeleteYnIsFalse(
            getJoinedChannelIds(loginId), teamId, ChannelType.DM);

        Map<Long, Long> otherUserIdByChannelId = dmChannels.stream()
            .collect(Collectors.toMap(Channel::getId, channel -> otherDmUserId(channel.getChannelName(), loginId)));
        Map<Long, User> users = userRepository.findAllById(otherUserIdByChannelId.values()).stream()
            .collect(Collectors.toMap(User::getId, Function.identity()));

        List<DmDto> dms = dmChannels.stream()
            .map(channel -> {
                Long otherUserId = otherUserIdByChannelId.get(channel.getId());
                User other = users.get(otherUserId);
                return new DmDto(channel.getId(), otherUserId, other == null ? null : other.getUserName());
            })
            .sorted(Comparator.comparing(DmDto::getChannelId))
            .toList();

        return ResponseData.success("DM 목록 조회", Map.of("list", dms));
    }

    /**
     * 같은 팀 멤버와의 DM 을 연다. 두 사람의 DM 이 이미 있으면 그 채널을 그대로 쓴다
     */
    @Transactional
    public ResponseData openDm(Long teamId, OpenDmRequestDto openDmRequestDto) {
        Long loginId = SecurityUtil.getLoginId();
        Long targetUserId = openDmRequestDto.getUserId();
        if (loginId.equals(targetUserId)) {
            throw new ApiException(ErrorCode.CANNOT_DM_SELF);
        }
        requireTeamMember(teamId, loginId);
        requireTeamMember(teamId, targetUserId);

        String dmName = dmChannelName(loginId, targetUserId);
        Channel channel = channelRepository.findByTeamIdAndChannelTypeAndChannelNameAndDeleteYnIsFalse(teamId, ChannelType.DM, dmName)
            .orElseGet(() -> saveChannel(teamId, dmName, ChannelType.DM));
        join(channel.getId(), loginId);
        join(channel.getId(), targetUserId);

        String targetUserName = userRepository.findById(targetUserId).map(User::getUserName).orElse(null);
        return ResponseData.success("DM 열기", new DmDto(channel.getId(), targetUserId, targetUserName));
    }

    /**
     * 채널 멤버 캐시를 MySQL 기준으로 다시 채우고 멤버 ID 를 돌려준다 (채팅 서버가 캐시 키가 없을 때 호출)
     */
    public List<Long> refreshMemberCache(Long channelId) {
        List<Long> memberIds = channelUserRepository.findAllByChannelIdAndDeleteYnIsFalse(channelId).stream()
            .map(ChannelUser::getUserId)
            .toList();
        channelMemberCache.replace(channelId, memberIds);
        return memberIds;
    }

    /**
     * 채널 멤버로 추가 (이미 멤버면 DB 는 그대로). MySQL 이 원본, Redis 는 전달용 캐시
     */
    public void join(Long channelId, Long userId) {
        if (!channelUserRepository.existsByChannelIdAndUserIdAndDeleteYnIsFalse(channelId, userId)) {
            channelUserRepository.save(ChannelUser.builder()
                .channelId(channelId)
                .userId(userId)
                .build());
        }
        channelMemberCache.add(channelId, userId);
    }

    public Channel saveChannel(Long teamId, String channelName, ChannelType channelType) {
        return channelRepository.save(Channel.builder()
            .teamId(teamId)
            .channelName(channelName)
            .channelType(channelType)
            .build());
    }

    /**
     * DM 채널 이름 : dm:{작은 userId}:{큰 userId} — 두 사람의 DM 을 이름 하나로 바로 찾기 위함
     */
    public static String dmChannelName(Long userId, Long otherUserId) {
        return "dm:" + Math.min(userId, otherUserId) + ":" + Math.max(userId, otherUserId);
    }

    private static Long otherDmUserId(String dmChannelName, Long loginId) {
        String[] userIds = dmChannelName.split(":");
        Long first = Long.valueOf(userIds[1]);
        Long second = Long.valueOf(userIds[2]);
        return first.equals(loginId) ? second : first;
    }

    private Set<Long> getJoinedChannelIds(Long userId) {
        return channelUserRepository.findAllByUserIdAndDeleteYnIsFalse(userId).stream()
            .map(ChannelUser::getChannelId)
            .collect(Collectors.toSet());
    }

    private void requireTeamMember(Long teamId, Long userId) {
        if (!teamUserRepository.existsByTeamIdAndUserIdAndDeleteYnIsFalse(teamId, userId)) {
            throw new ApiException(ErrorCode.NOT_TEAM_MEMBER);
        }
    }
}
