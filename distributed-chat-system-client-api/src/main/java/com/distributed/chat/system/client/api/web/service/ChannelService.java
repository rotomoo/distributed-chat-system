package com.distributed.chat.system.client.api.web.service;

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
import java.util.Objects;
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
    private final ChannelMessageService channelMessageService;

    /**
     * 팀의 공개 채널 목록 (참여 여부, 안 읽은 수 포함)
     */
    public ResponseData getChannels(Long teamId) {
        Long loginId = SecurityUtil.getLoginId();
        requireTeamMember(teamId, loginId);

        Map<Long, ChannelUser> memberships = getMemberships(loginId);
        List<Channel> publicChannels = channelRepository.findAllByTeamIdAndChannelTypeAndDeleteYnIsFalseOrderByIdAsc(teamId, ChannelType.PUBLIC);
        Map<Long, Integer> unreadCounts = countUnread(publicChannels, memberships, loginId);
        List<ChannelDto> channels = publicChannels.stream()
            .map(channel -> {
                ChannelUser membership = memberships.get(channel.getId());
                return new ChannelDto(channel.getId(), channel.getChannelName(), membership != null,
                    unreadCounts.getOrDefault(channel.getId(), 0), membership != null && membership.isNotificationMuted());
            })
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
     * 내 DM 목록 (안 읽은 수 포함)
     */
    public ResponseData getDms(Long teamId) {
        Long loginId = SecurityUtil.getLoginId();
        requireTeamMember(teamId, loginId);

        Map<Long, ChannelUser> memberships = getMemberships(loginId);
        List<Channel> dmChannels = channelRepository.findAllByIdInAndTeamIdAndChannelTypeAndDeleteYnIsFalse(
            memberships.keySet(), teamId, ChannelType.DM);

        Map<Long, Integer> unreadCounts = countUnread(dmChannels, memberships, loginId);
        Map<Long, Long> otherUserIdByChannelId = dmChannels.stream()
            .collect(Collectors.toMap(Channel::getId, channel -> otherDmUserId(channel.getChannelName(), loginId)));
        Map<Long, User> users = userRepository.findAllById(otherUserIdByChannelId.values()).stream()
            .collect(Collectors.toMap(User::getId, Function.identity()));

        List<DmDto> dms = dmChannels.stream()
            .map(channel -> {
                Long otherUserId = otherUserIdByChannelId.get(channel.getId());
                User other = users.get(otherUserId);
                return new DmDto(channel.getId(), otherUserId, other == null ? null : other.getUserName(),
                    unreadCounts.getOrDefault(channel.getId(), 0), memberships.get(channel.getId()).isNotificationMuted());
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
     * 이 채널(DM 포함)의 웹 푸시 알림 끄기 / 켜기. 채널 멤버만 (아니면 3003). notification-consumer 가 알림 보낼 때 읽는다
     */
    @Transactional
    public ResponseData setNotificationMuted(Long channelId, boolean muted) {
        Long loginId = SecurityUtil.getLoginId();
        if (channelUserRepository.updateNotificationMuted(channelId, loginId, muted) == 0) {
            throw new ApiException(ErrorCode.NOT_CHANNEL_MEMBER);
        }

        return ResponseData.success("채널 알림 설정", Map.of("channelId", channelId, "muted", muted));
    }

    /**
     * 채널 멤버로 추가 (이미 멤버면 그대로). 채널 멤버는 MySQL channel_user 에만 있다 (컨슈머가 메시지마다 여기서 읽는다)
     */
    public void join(Long channelId, Long userId) {
        if (!channelUserRepository.existsByChannelIdAndUserIdAndDeleteYnIsFalse(channelId, userId)) {
            channelUserRepository.save(ChannelUser.builder()
                .channelId(channelId)
                .userId(userId)
                .build());
        }
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

    /**
     * 내가 참여한 채널 → 내 멤버십 (마지막으로 읽은 메시지 포함)
     */
    private Map<Long, ChannelUser> getMemberships(Long userId) {
        return channelUserRepository.findAllByUserIdAndDeleteYnIsFalse(userId).stream()
            .collect(Collectors.toMap(ChannelUser::getChannelId, Function.identity()));
    }

    /**
     * 목록에 보이는 채널 중 참여한 채널의 안 읽은 수 (집계 한 번). 참여 안 한 채널은 결과에 없다 = 0
     */
    private Map<Long, Integer> countUnread(List<Channel> channels, Map<Long, ChannelUser> memberships, Long loginId) {
        List<ChannelUser> joined = channels.stream()
            .map(channel -> memberships.get(channel.getId()))
            .filter(Objects::nonNull)
            .toList();
        return channelMessageService.countUnread(joined, loginId, ChannelMessageService.UNREAD_COUNT_CAP);
    }

    private void requireTeamMember(Long teamId, Long userId) {
        if (!teamUserRepository.existsByTeamIdAndUserIdAndDeleteYnIsFalse(teamId, userId)) {
            throw new ApiException(ErrorCode.NOT_TEAM_MEMBER);
        }
    }
}
