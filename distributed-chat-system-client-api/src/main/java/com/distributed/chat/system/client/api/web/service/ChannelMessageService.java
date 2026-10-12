package com.distributed.chat.system.client.api.web.service;

import com.distributed.chat.system.client.api.base.security.SecurityUtil;
import com.distributed.chat.system.client.api.web.dto.channel.ChannelMessageDto;
import com.distributed.chat.system.common.exception.ApiException;
import com.distributed.chat.system.common.exception.ErrorCode;
import com.distributed.chat.system.common.response.ResponseData;
import com.distributed.chat.system.mongodb.collection.ChatMessage;
import com.distributed.chat.system.mongodb.repository.ChatMessageRepository;
import com.distributed.chat.system.mysql.repository.ChannelUserRepository;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.bson.types.ObjectId;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;

/**
 * 채널 메시지 이력 (MongoDB). 채널 멤버만 볼 수 있다
 */
@Service
@RequiredArgsConstructor
public class ChannelMessageService {

    public static final int MAX_PAGE_SIZE = 100;

    private final ChatMessageRepository chatMessageRepository;
    private final ChannelUserRepository channelUserRepository;

    /**
     * before 보다 이전 메시지를 size 개 (없으면 최신부터). 화면에 바로 쓰도록 오래된 순으로 돌려준다
     */
    public ResponseData getMessages(Long channelId, String before, int size) {
        Long loginId = SecurityUtil.getLoginId();
        if (!channelUserRepository.existsByChannelIdAndUserIdAndDeleteYnIsFalse(channelId, loginId)) {
            throw new ApiException(ErrorCode.NOT_CHANNEL_MEMBER);
        }
        if (before != null && !ObjectId.isValid(before)) {
            throw new ApiException(ErrorCode.INVALID_MESSAGE_CURSOR);
        }

        PageRequest page = PageRequest.of(0, Math.min(Math.max(size, 1), MAX_PAGE_SIZE));
        List<ChatMessage> newestFirst = before == null
            ? chatMessageRepository.findByChannelIdOrderByIdDesc(channelId, page)
            : chatMessageRepository.findByChannelIdAndIdLessThanOrderByIdDesc(channelId, new ObjectId(before), page);

        List<ChannelMessageDto> messages = new ArrayList<>(newestFirst.stream()
            .map(message -> new ChannelMessageDto(
                message.getId(),
                message.getChannelId(),
                message.getCreateUserId(),
                message.getContent(),
                Optional.ofNullable(message.getEventPublishDt()).map(Instant::toEpochMilli).orElse(null)))
            .toList());
        Collections.reverse(messages);

        return ResponseData.success("채널 메시지 조회", Map.of("list", messages));
    }
}
