package com.distributed.chat.system.client.api.web.service;

import com.distributed.chat.system.client.api.base.security.SecurityUtil;
import com.distributed.chat.system.client.api.web.dto.user.GetMyInfoResponseDto;
import com.distributed.chat.system.common.exception.ApiException;
import com.distributed.chat.system.common.exception.ErrorCode;
import com.distributed.chat.system.common.response.ResponseData;
import com.distributed.chat.system.mysql.entity.User;
import com.distributed.chat.system.mysql.repository.UserRepository;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
@Slf4j
public class UserService {

    private final UserRepository userRepository;

    private final RedisTemplate redisTemplate;


    /**
     * 내 정보 조회
     *
     * @return
     */
    public ResponseData getMyInfo() {
        Long loginId = SecurityUtil.getLoginId();

        Optional<User> foundUser = userRepository.findById(loginId);

        if (foundUser.isEmpty()) {
            throw new ApiException(ErrorCode.NOT_EXIST_USER_INFO);
        }

        User user = foundUser.get();
        String chatServerIp = (String) redisTemplate.opsForValue().get(String.valueOf(loginId));

        return ResponseData.success("내 정보 조회", new GetMyInfoResponseDto(user, chatServerIp));
    }
}
