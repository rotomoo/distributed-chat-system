package com.distributed.chat.system.common.event;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.util.List;

/**
 * 컨슈머 → 채팅 서버 (POST /internal/chat/deliver) : 그 채팅 서버에 접속한 수신자에게 전달할 메시지. 호환 규칙은 {@link EventJson}
 *
 * @param messageId      저장된 메시지 ID (ObjectId 24자리 hex)
 * @param chatMessage    원래 메시지 이벤트 (없으면 빈 값)
 * @param receiveUserIds 이 채팅 서버에서 받을 사람 (최대 500명씩 나눠 보낸다)
 * @param attachments    컨슈머가 GridFS 로 확인한 첨부만 (chatMessage.attachmentFileIds 는 클라이언트가 보낸 값)
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record ChatMessageDelivery(
    String messageId,
    ChatMessageEvent chatMessage,
    List<Long> receiveUserIds,
    List<Attachment> attachments
) {

    public ChatMessageDelivery {
        messageId = EventDefaults.text(messageId);
        chatMessage = chatMessage == null ? ChatMessageEvent.empty() : chatMessage;
        receiveUserIds = EventDefaults.list(receiveUserIds);
        attachments = EventDefaults.list(attachments);
    }

    /**
     * 컨슈머가 GridFS metadata 로 확인한 첨부파일
     *
     * @param fileId      GridFS 파일 ID
     * @param name        원래 파일 이름
     * @param contentType 형식 (MIME)
     * @param size        크기 (바이트)
     */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Attachment(String fileId, String name, String contentType, long size) {

        public Attachment {
            fileId = EventDefaults.text(fileId);
            name = EventDefaults.text(name);
            contentType = EventDefaults.text(contentType);
        }
    }
}
