package com.distributed.chat.system.notification.service;

import com.distributed.chat.system.mysql.entity.Channel;
import com.distributed.chat.system.mysql.enums.ChannelType;
import protobuf.EdaChatMessage.ChatNotification;

/**
 * 서비스 워커가 받는 푸시 본문 (JSON). 서비스 워커는 이걸로 showNotification(title, {body, tag, data: {url}}) 한다
 *
 * @param title 공개 채널은 "#채널명", DM 은 보낸 사람 이름
 * @param body  "보낸 사람: 미리보기". 본문 없이 첨부만이면 "보낸 사람: 파일 N개"
 * @param url   누르면 열 화면 "/?channel={channelId}"
 * @param tag   같은 채널 알림은 브라우저에서 하나로 바뀐다 "channel-{channelId}"
 */
public record WebPushPayload(String title, String body, String url, String tag) {

    static final String UNKNOWN_SENDER = "알 수 없는 사용자";

    public static WebPushPayload of(Channel channel, String senderName, ChatNotification notification) {
        String sender = senderName == null || senderName.isBlank() ? UNKNOWN_SENDER : senderName;
        String title = channel.getChannelType() == ChannelType.DM ? sender : "#" + channel.getChannelName();
        return new WebPushPayload(title, sender + ": " + summary(notification), "/?channel=" + channel.getId(), tag(channel.getId()));
    }

    /**
     * 같은 채널의 알림을 묶는 값 (브라우저 tag, 푸시 서비스 Topic 헤더 둘 다). Topic 은 base64url 문자 32자 이하라 이 모양이면 된다
     */
    public static String tag(Long channelId) {
        return "channel-" + channelId;
    }

    private static String summary(ChatNotification notification) {
        if (!notification.getPreview().isBlank()) {
            return notification.getPreview();
        }
        if (notification.getAttachmentCount() > 0) {
            return "파일 " + notification.getAttachmentCount() + "개";
        }
        return "새 메시지";
    }
}
