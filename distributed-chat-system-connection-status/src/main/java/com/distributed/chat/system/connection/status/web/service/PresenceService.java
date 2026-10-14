package com.distributed.chat.system.connection.status.web.service;

import com.distributed.chat.system.common.exception.ApiException;
import com.distributed.chat.system.common.exception.ErrorCode;
import com.distributed.chat.system.common.redis.ChatPresenceKeys;
import com.distributed.chat.system.common.response.ResponseData;
import com.distributed.chat.system.connection.status.base.security.SecurityUtil;
import com.distributed.chat.system.connection.status.web.dto.presence.UserPresenceDto;
import com.distributed.chat.system.mysql.entity.TeamUser;
import com.distributed.chat.system.mysql.repository.TeamUserRepository;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

/**
 * 팀 멤버 접속 상태 조회. 접속 상태를 읽는 곳은 이 서버 하나 (쓰는 쪽은 채팅 서버, Redis presence:{userId})
 * <ul>
 *   <li>팀 멤버 : MySQL team_user 1번 (로그인 사용자가 그 안에 없으면 2003)</li>
 *   <li>접속 상태 : 멤버 전체를 MGET 1번. 키가 없으면 offline (채팅 서버가 죽어도 TTL 60초 뒤 사라진다)</li>
 * </ul>
 */
@Service
@RequiredArgsConstructor
public class PresenceService {

    private final TeamUserRepository teamUserRepository;
    private final RedisTemplate<String, Object> redisTemplate;

    public ResponseData getTeamPresence(Long teamId) {
        Long loginId = SecurityUtil.getLoginId();

        // 멤버 목록을 한 번 읽어서 멤버 확인까지 한다 (멤버 확인용 쿼리를 따로 하지 않는다)
        List<Long> userIds = teamUserRepository.findAllByTeamIdAndDeleteYnIsFalse(teamId).stream()
            .map(TeamUser::getUserId)
            .sorted()
            .toList();
        if (!userIds.contains(loginId)) {
            throw new ApiException(ErrorCode.NOT_TEAM_MEMBER);
        }

        List<Object> presences = redisTemplate.opsForValue()
            .multiGet(userIds.stream().map(ChatPresenceKeys::presenceKey).toList());
        List<UserPresenceDto> list = new ArrayList<>(userIds.size());
        for (int i = 0; i < userIds.size(); i++) {
            Object presence = presences == null ? null : presences.get(i);
            list.add(new UserPresenceDto(userIds.get(i), ChatPresenceKeys.statusOf(presence)));
        }

        return ResponseData.success("팀 접속 상태 조회", Map.of("list", list));
    }
}
