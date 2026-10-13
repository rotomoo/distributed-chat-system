package com.distributed.chat.system.client.api.web.service;

import com.distributed.chat.system.client.api.base.security.SecurityUtil;
import com.distributed.chat.system.client.api.web.dto.channel.CatchUpDto;
import com.distributed.chat.system.client.api.web.dto.channel.ChannelMessageDto;
import com.distributed.chat.system.client.api.web.dto.channel.ReactionDto;
import com.distributed.chat.system.client.api.web.dto.file.FileDto;
import com.distributed.chat.system.common.exception.ApiException;
import com.distributed.chat.system.common.exception.ErrorCode;
import com.distributed.chat.system.common.response.ResponseData;
import com.distributed.chat.system.mongodb.collection.ChatMessage;
import com.distributed.chat.system.mongodb.repository.ChatMessageRepository;
import com.distributed.chat.system.mysql.entity.ChannelUser;
import com.distributed.chat.system.mysql.repository.ChannelUserRepository;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.bson.types.ObjectId;
import org.springframework.data.domain.Slice;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 채널 메시지 이력 (MongoDB) 과 읽음 위치. 채널 멤버만 볼 수 있다
 */
@Service
@RequiredArgsConstructor
public class ChannelMessageService {

    public static final int MAX_PAGE_SIZE = 100;

    /**
     * 채널 / DM 목록의 안 읽은 수는 여기까지만 센다 (화면에는 99+ 정도면 충분)
     */
    public static final int UNREAD_COUNT_CAP = 100;

    private final ChatMessageRepository chatMessageRepository;
    private final ChannelUserRepository channelUserRepository;
    private final ChannelMemberGuard channelMemberGuard;

    /**
     * 화면에 바로 쓰도록 오래된 순으로 돌려준다.
     * <ul>
     *   <li>before : 그보다 이전 메시지 size 개 (위로 스크롤)</li>
     *   <li>after : 그보다 뒤의 메시지 size 개 (재접속 시 빈틈 채우기)</li>
     *   <li>둘 다 없으면 최신 size 개</li>
     * </ul>
     * hasNext : 최신 / before 는 이보다 더 이전 메시지가 있는지, after 는 이보다 더 최신 메시지가 있는지
     */
    public ResponseData getMessages(Long channelId, String before, String after, int size) {
        Long loginId = SecurityUtil.getLoginId();
        channelMemberGuard.requireMember(channelId, loginId);
        if (before != null && after != null) {
            throw new ApiException(ErrorCode.INVALID_MESSAGE_CURSOR);
        }
        if ((before != null && !ObjectId.isValid(before)) || (after != null && !ObjectId.isValid(after))) {
            throw new ApiException(ErrorCode.INVALID_MESSAGE_CURSOR);
        }

        int pageSize = Math.min(Math.max(size, 1), MAX_PAGE_SIZE);
        Slice<ChatMessage> slice;
        List<ChatMessage> oldestFirst;
        if (after != null) {
            slice = chatMessageRepository.findMessagesAfter(channelId, new ObjectId(after), pageSize);
            oldestFirst = slice.getContent();
        } else {
            slice = before == null
                ? chatMessageRepository.findLatestMessages(channelId, pageSize)
                : chatMessageRepository.findMessagesBefore(channelId, new ObjectId(before), pageSize);
            oldestFirst = new ArrayList<>(slice.getContent());
            Collections.reverse(oldestFirst);
        }

        List<ChannelMessageDto> messages = oldestFirst.stream().map(ChannelMessageService::toDto).toList();

        return ResponseData.success("채널 메시지 조회", Map.of("list", messages, "hasNext", slice.hasNext()));
    }

    /**
     * 여기까지 읽음. 이미 더 뒤까지 읽었으면 그대로 둔다 (읽음 위치는 앞으로만 간다).
     * 형식이 틀리거나 그 채널의 메시지가 아니면 4001, 채널 멤버가 아니면 3003
     */
    @Transactional
    public ResponseData markRead(Long channelId, String messageId) {
        Long loginId = SecurityUtil.getLoginId();
        if (messageId == null || !ObjectId.isValid(messageId)) {
            throw new ApiException(ErrorCode.INVALID_MESSAGE_CURSOR);
        }

        // 그 채널에 실제로 있는 메시지만 읽음 위치가 될 수 있다 (ffff... 같은 값으로 안 읽은 수를 영원히 0 으로 만들지 못하게)
        ObjectId readUpTo = new ObjectId(messageId);
        if (!chatMessageRepository.existsInChannel(readUpTo, channelId)) {
            throw new ApiException(ErrorCode.INVALID_MESSAGE_CURSOR);
        }

        // 소문자 hex 로 맞춰야 문자열 비교가 ObjectId 순서와 같다
        int updated = channelUserRepository.moveLastReadMessageIdForward(channelId, loginId, readUpTo.toHexString());
        if (updated == 0) {
            channelMemberGuard.requireMember(channelId, loginId);
        }

        return ResponseData.success("읽음 처리", null);
    }

    /**
     * 안 읽은 수와 안 읽은 첫 메시지 (구분선 위치). 읽음 처리는 하지 않는다
     */
    public ResponseData getCatchUp(Long channelId) {
        Long loginId = SecurityUtil.getLoginId();
        ChannelUser membership = channelUserRepository.findByChannelIdAndUserIdAndDeleteYnIsFalse(channelId, loginId)
            .orElseThrow(() -> new ApiException(ErrorCode.NOT_CHANNEL_MEMBER));
        ObjectId unreadAfter = unreadAfter(membership);

        long unreadCount = chatMessageRepository.countUnread(channelId, unreadAfter, loginId, 0);
        String firstUnreadMessageId = unreadCount == 0 ? null
            : chatMessageRepository.findFirstUnread(channelId, unreadAfter, loginId).map(message -> message.getId().toHexString()).orElse(null);

        return ResponseData.success("안 읽은 메시지 따라잡기",
            new CatchUpDto((int) Math.min(unreadCount, Integer.MAX_VALUE), firstUnreadMessageId));
    }

    /**
     * 채널 / DM 목록용 안 읽은 수. 여러 채널을 집계 한 번으로 세고, 채널마다 cap 까지만 센다
     *
     * @param memberships 내 멤버십 (같은 사람)
     * @return 채널 ID → 안 읽은 수 (0 인 채널은 빠질 수 있다)
     */
    public Map<Long, Integer> countUnread(Collection<ChannelUser> memberships, Long readerUserId, int cap) {
        if (memberships.isEmpty()) {
            return Map.of();
        }
        Map<Long, ObjectId> unreadAfterByChannel = new LinkedHashMap<>();
        memberships.forEach(membership -> unreadAfterByChannel.put(membership.getChannelId(), unreadAfter(membership)));
        return chatMessageRepository.countUnreadByChannel(unreadAfterByChannel, readerUserId, cap);
    }

    /**
     * 안 읽은 기준 = 마지막으로 읽은 메시지와 채널에 들어온 시각 중 뒤의 것.
     * 한 번도 읽음 처리를 안 했어도 들어오기 전 메시지는 안 읽은 것으로 세지 않는다
     * (들어온 시각은 초 단위라 같은 초에 먼저 온 메시지는 안 읽은 것으로 셀 수 있다)
     */
    static ObjectId unreadAfter(ChannelUser membership) {
        ObjectId lastRead = toObjectId(membership.getLastReadMessageId());
        ObjectId joined = membership.getCreateDt() == null ? null : ObjectId.getSmallestWithDate(Date.from(membership.getCreateDt()));
        if (lastRead == null) {
            return joined;
        }
        if (joined == null) {
            return lastRead;
        }
        return lastRead.compareTo(joined) >= 0 ? lastRead : joined;
    }

    private static ChannelMessageDto toDto(ChatMessage message) {
        List<FileDto> attachments = Optional.ofNullable(message.getAttachments()).orElse(List.of()).stream()
            .map(attachment -> new FileDto(attachment.fileId(), attachment.name(), attachment.contentType(), attachment.size()))
            .toList();
        List<ReactionDto> reactions = Optional.ofNullable(message.getReactions()).orElse(List.of()).stream()
            .map(reaction -> new ReactionDto(reaction.emoji(), Optional.ofNullable(reaction.userIds()).orElse(List.of())))
            .toList();
        return new ChannelMessageDto(
            message.getId().toHexString(),
            message.getChannelId(),
            message.getCreateUserId(),
            message.getContent(),
            Optional.ofNullable(message.getEventPublishDt()).map(Instant::toEpochMilli).orElse(null),
            attachments,
            reactions);
    }

    private static ObjectId toObjectId(String messageId) {
        return messageId != null && ObjectId.isValid(messageId) ? new ObjectId(messageId) : null;
    }
}
