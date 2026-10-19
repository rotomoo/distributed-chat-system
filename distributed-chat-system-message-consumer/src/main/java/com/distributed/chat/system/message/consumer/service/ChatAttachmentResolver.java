package com.distributed.chat.system.message.consumer.service;

import com.distributed.chat.system.common.event.ChatMessageEvent;
import com.distributed.chat.system.mongodb.collection.ChatMessage.Attachment;
import com.distributed.chat.system.mongodb.file.ChatFile;
import com.distributed.chat.system.mongodb.file.ChatFileStore;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * 메시지에 붙은 fileId 를 GridFS metadata 로 확인해서 첨부 정보(이름/형식/크기)를 채운다.
 * 클라이언트가 보낸 값은 fileId 만 믿고, 그 파일이 이 채널에 보낸 사람이 올린 것이 아니면 버린다
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class ChatAttachmentResolver {

    private final ChatFileStore chatFileStore;

    /**
     * @return 보낸 순서 그대로, 통과한 첨부만 (같은 fileId 는 한 번)
     */
    public List<Attachment> resolve(ChatMessageEvent chatMessage) {
        List<String> fileIds = chatMessage.attachmentFileIds().stream().distinct().toList();
        if (fileIds.isEmpty()) {
            return List.of();
        }

        Map<String, ChatFile> filesById = chatFileStore.findAllById(fileIds).stream()
            .collect(Collectors.toMap(ChatFile::fileId, Function.identity()));

        return fileIds.stream()
            .map(fileId -> {
                ChatFile file = filesById.get(fileId);
                if (file == null || !Objects.equals(file.channelId(), chatMessage.channelId())
                    || !Objects.equals(file.uploaderId(), chatMessage.sendUserId())) {
                    log.warn("attachment dropped fileId : {}, channelId : {}, sendUserId : {}",
                        fileId, chatMessage.channelId(), chatMessage.sendUserId());
                    return null;
                }
                return new Attachment(file.fileId(), file.originalName(), file.contentType(), file.size());
            })
            .filter(Objects::nonNull)
            .toList();
    }
}
