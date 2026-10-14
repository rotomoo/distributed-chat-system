package com.distributed.chat.system.chatting.base.handler;

import com.distributed.chat.system.chatting.base.presence.UserLocationRegistry;
import com.distributed.chat.system.chatting.web.controller.feign.KafkaProducerClient;
import com.distributed.chat.system.common.exception.ErrorCode;
import com.distributed.chat.system.common.util.NetworkUtil;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.time.Clock;
import java.time.Duration;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import lombok.extern.slf4j.Slf4j;
import org.bson.types.ObjectId;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.ConcurrentWebSocketSessionDecorator;
import org.springframework.web.socket.handler.TextWebSocketHandler;
import protobuf.EdaChatMessage.ChatMessage;
import protobuf.EdaChatMessage.ChatMessageDelivery;
import protobuf.EdaChatMessage.ChatReaction;
import protobuf.EdaChatMessage.ChatReactionDelivery;

/**
 * 한 유저가 여러 기기(PC, 폰, 탭)로 동시에 접속할 수 있다.
 * <ul>
 *   <li>서버 메모리 : userId → { 웹소켓 세션ID → 세션 } (기기마다 소켓이 따로 있으므로 모두 보관)</li>
 *   <li>Redis {@code {userId}} : 그 유저의 기기들이 모인 채팅 서버 ({@link UserLocationRegistry}). 컨슈머 / api-gateway 가 읽는다</li>
 * </ul>
 * 서버 안의 경쟁(같은 유저의 동시 접속/해제)은 {@link ConcurrentHashMap#compute} 로 유저 단위 직렬화한다.
 * <p>
 * 채팅 서버는 메시지 / 리액션의 형식만 검사하고 발행한다. 채널 멤버인지는 컨슈머가 원본(MySQL channel_user)에서 한 번만 확인한다
 */
@Slf4j
@Component
public class CustomTextWebSocketHandler extends TextWebSocketHandler {

    private static final int MAX_MESSAGE_LENGTH = 4000;
    private static final int SEND_TIME_LIMIT_MS = 5000;
    private static final int SEND_BUFFER_SIZE_LIMIT = 512 * 1024;

    /**
     * 이 유저는 다른 채팅 서버에 붙어 있다 (유저 하나 = 서버 하나). 클라이언트는 그냥 다시 붙으면 되고,
     * 다시 붙을 때 api-gateway 가 Redis {userId} 를 보고 그 서버로 보낸다. 409(Conflict) 를 본뜬 앱 정의 코드
     */
    public static final CloseStatus CONNECTED_TO_OTHER_SERVER = new CloseStatus(4409, "connected to another chat server");
    private static final int MAX_ATTACHMENTS = 10;
    private static final int MAX_EMOJI_LENGTH = 32;

    private static final TextMessage PING = new TextMessage("{\"type\":\"ping\"}");

    private final Map<String, Map<String, WebSocketSession>> webSocketSessionMap = new ConcurrentHashMap<>();

    /**
     * 웹소켓 세션 ID → 마지막으로 프레임을 받은 시각 (epoch millis). 하트비트가 응답 없는 소켓을 찾는 데 쓴다
     */
    private final Map<String, Long> lastSeenAtBySession = new ConcurrentHashMap<>();
    private final KafkaProducerClient kafkaProducerClient;
    private final UserLocationRegistry userLocationRegistry;
    private final ObjectMapper objectMapper;
    private final Clock clock;

    public CustomTextWebSocketHandler(KafkaProducerClient kafkaProducerClient, UserLocationRegistry userLocationRegistry,
        ObjectMapper objectMapper, Clock clock) {
        this.kafkaProducerClient = kafkaProducerClient;
        this.userLocationRegistry = userLocationRegistry;
        this.objectMapper = objectMapper;
        this.clock = clock;
    }

    @Override
    public void afterConnectionEstablished(WebSocketSession webSocketSession) throws Exception {
        log.info("{} connected", webSocketSession.getId());

        String serverIp = NetworkUtil.getServerIp();
        String chatServerUri = serverIp + ":" + webSocketSession.getLocalAddress().getPort();
        String userId = (String) webSocketSession.getAttributes().get("userId");
        webSocketSession.getAttributes().put("chatServerUri", chatServerUri);

        // 컨슈머 전달(HTTP 스레드)과 다른 메시지 전송이 동시에 일어날 수 있어 thread-safe 하게 감싼다
        WebSocketSession concurrentSession = new ConcurrentWebSocketSessionDecorator(
            webSocketSession, SEND_TIME_LIMIT_MS, SEND_BUFFER_SIZE_LIMIT);

        // 같은 유저의 접속/해제가 끼어들지 않도록 Redis 갱신까지 유저 단위로 묶는다
        AtomicBoolean accepted = new AtomicBoolean();
        this.webSocketSessionMap.compute(userId, (key, sessions) -> {
            if (!userLocationRegistry.claim(userId, chatServerUri)) {
                return sessions;
            }
            accepted.set(true);
            Map<String, WebSocketSession> userSessions = sessions == null ? new ConcurrentHashMap<>() : sessions;
            userSessions.put(webSocketSession.getId(), concurrentSession);
            return userSessions;
        });

        if (!accepted.get()) {
            // 다른 살아 있는 서버에 이 유저의 기기가 있다 → 닫으면 클라이언트가 다시 붙고 api-gateway 가 그 서버로 보낸다
            log.info("{} userId : {} is on another chat server, close {}", webSocketSession.getId(), userId,
                CONNECTED_TO_OTHER_SERVER.getCode());
            webSocketSession.close(CONNECTED_TO_OTHER_SERVER);
            return;
        }
        lastSeenAtBySession.put(webSocketSession.getId(), clock.millis());

        // 접속 정보 기록까지 끝났음을 알린다. 클라이언트는 이 신호를 받아야 "연결됨"으로 본다.
        // (api-gateway 는 채팅 서버에 붙기 전에 클라이언트에 101 을 먼저 보내므로, 101 만으로는 실제 연결을 보장하지 못한다)
        concurrentSession.sendMessage(new TextMessage(objectMapper.writeValueAsString(Map.of(
            "type", "connected",
            "chatServer", chatServerUri
        ))));
    }

    /**
     * 클라이언트 → 서버
     * <ul>
     *   <li>{"type": "message", "channelId": 1, "message": "안녕하세요", "attachments": [{"fileId": "..."}]}
     *       : 첨부가 있으면 message 는 비어도 된다. 첨부 최대 10개 (이름/형식/크기는 컨슈머가 GridFS 에서 채운다)</li>
     *   <li>{"type": "reaction", "channelId": 1, "messageId": "...", "emoji": "👍", "action": "add" | "remove"}</li>
     * </ul>
     * 형식이 틀리면 보낸 사람에게 {"type": "error", ...} 를 돌려주고 발행하지 않는다 (채널 멤버 확인은 컨슈머가 한다)
     */
    @Override
    public void handleMessage(WebSocketSession webSocketSession, WebSocketMessage<?> message) throws Exception {
        // 어떤 프레임이든 오면 살아 있는 것 (pong 이 아니어도)
        lastSeenAtBySession.put(webSocketSession.getId(), clock.millis());
        String userId = (String) webSocketSession.getAttributes().get("userId");

        JsonNode payload;
        try {
            payload = objectMapper.readTree(message.getPayload().toString());
        } catch (IOException e) {
            sendError(webSocketSession, ErrorCode.INVALID_CHAT_MESSAGE);
            return;
        }

        switch (payload.path("type").asText()) {
            case "message" -> handleChatMessage(webSocketSession, userId, payload);
            case "reaction" -> handleReaction(webSocketSession, userId, payload);
            case "pong" -> {
                // 하트비트 응답. 위에서 마지막 수신 시각을 이미 갱신했다
            }
            default -> sendError(webSocketSession, ErrorCode.INVALID_CHAT_MESSAGE);
        }
    }

    private void handleChatMessage(WebSocketSession webSocketSession, String userId, JsonNode payload) throws IOException {
        String text = payload.path("message").asText("");
        long channelId = payload.path("channelId").asLong(0);
        List<String> attachmentFileIds = attachmentFileIds(payload.path("attachments"));
        if (channelId <= 0 || attachmentFileIds == null || text.length() > MAX_MESSAGE_LENGTH
            || (text.isBlank() && attachmentFileIds.isEmpty())) {
            sendError(webSocketSession, ErrorCode.INVALID_CHAT_MESSAGE);
            return;
        }

        log.info("{} sent to channel {}", webSocketSession.getId(), channelId);

        ChatMessage chatMessage = ChatMessage.newBuilder()
            .setSendUserId(Long.parseLong(userId))
            .setChannelId(channelId)
            .setMessage(text)
            .addAllAttachmentFileIds(attachmentFileIds)
            // 컨슈머가 이 값으로 한 번만 저장한다 (재시도 / 중복 발행에도 문서 1개). 메시지 ID(_id)는 저장할 때 만든다
            .setEventId(UUID.randomUUID().toString())
            .setPublishDt(System.currentTimeMillis())
            .build();

        kafkaProducerClient.publishChatSendMessage(chatMessage);
    }

    private void handleReaction(WebSocketSession webSocketSession, String userId, JsonNode payload) throws IOException {
        long channelId = payload.path("channelId").asLong(0);
        String messageId = payload.path("messageId").asText("");
        String emoji = payload.path("emoji").asText("");
        ChatReaction.Action action = switch (payload.path("action").asText()) {
            case "add" -> ChatReaction.Action.ADD;
            case "remove" -> ChatReaction.Action.REMOVE;
            default -> null;
        };
        if (channelId <= 0 || !isObjectId(messageId) || !isValidEmoji(emoji) || action == null) {
            sendError(webSocketSession, ErrorCode.INVALID_CHAT_MESSAGE);
            return;
        }


        kafkaProducerClient.publishChatReaction(ChatReaction.newBuilder()
            .setSendUserId(Long.parseLong(userId))
            .setChannelId(channelId)
            .setMessageId(messageId.toLowerCase())
            .setEmoji(emoji)
            .setAction(action)
            .setPublishDt(System.currentTimeMillis())
            .build());
    }

    /**
     * attachments : [{"fileId": "ObjectId"}] (없으면 빈 목록). 형식이 틀리거나 10개를 넘으면 null.
     * 채팅 서버는 형식만 본다 (파일이 이 채널에 보낸 사람이 올린 것인지는 컨슈머가 GridFS 로 확인)
     */
    private static List<String> attachmentFileIds(JsonNode attachments) {
        if (attachments.isMissingNode() || attachments.isNull()) {
            return List.of();
        }
        if (!attachments.isArray() || attachments.size() > MAX_ATTACHMENTS) {
            return null;
        }
        Set<String> fileIds = new LinkedHashSet<>();
        for (JsonNode attachment : attachments) {
            String fileId = attachment.path("fileId").asText("");
            if (!isObjectId(fileId)) {
                return null;
            }
            fileIds.add(fileId.toLowerCase());
        }
        return List.copyOf(fileIds);
    }

    private static boolean isObjectId(String value) {
        return ObjectId.isValid(value);
    }

    /**
     * 공백 없는 1~32자 (글자 수는 코드 포인트 기준. 피부색 / 합자 이모지는 여러 코드 포인트)
     */
    private static boolean isValidEmoji(String emoji) {
        int length = emoji.codePointCount(0, emoji.length());
        return length >= 1 && length <= MAX_EMOJI_LENGTH && emoji.codePoints().noneMatch(Character::isWhitespace);
    }

    private void sendError(WebSocketSession webSocketSession, ErrorCode errorCode) throws IOException {
        String userId = (String) webSocketSession.getAttributes().get("userId");
        WebSocketSession concurrentSession = getSessions(userId).stream()
            .filter(session -> session.getId().equals(webSocketSession.getId()))
            .findFirst()
            .orElse(webSocketSession);
        concurrentSession.sendMessage(new TextMessage(objectMapper.writeValueAsString(Map.of(
            "type", "error",
            "code", errorCode.getCustomCode(),
            "message", errorCode.getMsg()
        ))));
    }

    @Override
    public void afterConnectionClosed(WebSocketSession webSocketSession, CloseStatus status)
        throws Exception {
        log.info("{} disconnected", webSocketSession.getId());
        lastSeenAtBySession.remove(webSocketSession.getId());

        String userId = (String) webSocketSession.getAttributes().get("userId");
        String chatServerUri = (String) webSocketSession.getAttributes().get("chatServerUri");

        // 끊긴 기기만 뺀다. 이 서버에서 그 유저의 마지막 기기일 때만 Redis {userId} 를 지운다 (값이 아직 이 서버일 때만)
        this.webSocketSessionMap.computeIfPresent(userId, (key, userSessions) -> {
            if (userSessions.remove(webSocketSession.getId()) == null || !userSessions.isEmpty()) {
                return userSessions.isEmpty() ? null : userSessions;
            }
            userLocationRegistry.release(userId, chatServerUri);
            return null;
        });
    }

    /**
     * 컨슈머가 전달한 메시지를 이 서버에 접속한 수신자들의 모든 기기에 전송
     */
    public void deliver(ChatMessageDelivery delivery) {
        ChatMessage chatMessage = delivery.getChatMessage();
        Map<String, Object> frame = new LinkedHashMap<>();
        frame.put("type", "message");
        frame.put("messageId", delivery.getMessageId());
        frame.put("channelId", chatMessage.getChannelId());
        frame.put("sendUserId", chatMessage.getSendUserId());
        frame.put("message", chatMessage.getMessage());
        frame.put("publishDt", chatMessage.getPublishDt());
        frame.put("attachments", delivery.getAttachmentsList().stream()
            .map(attachment -> Map.of(
                "fileId", attachment.getFileId(),
                "name", attachment.getName(),
                "contentType", attachment.getContentType(),
                "size", attachment.getSize()))
            .toList());

        sendToReceivers(frame, delivery.getReceiveUserIdsList());
    }

    /**
     * 컨슈머가 전달한 리액션 변경(바뀐 뒤의 reactions 전체)을 이 서버에 접속한 수신자들의 모든 기기에 전송
     */
    public void deliverReaction(ChatReactionDelivery delivery) {
        Map<String, Object> frame = new LinkedHashMap<>();
        frame.put("type", "reaction");
        frame.put("channelId", delivery.getChannelId());
        frame.put("messageId", delivery.getMessageId());
        frame.put("reactions", delivery.getReactionsList().stream()
            .map(reaction -> Map.of("emoji", reaction.getEmoji(), "userIds", reaction.getUserIdsList()))
            .toList());

        sendToReceivers(frame, delivery.getReceiveUserIdsList());
    }

    /**
     * 직렬화는 한 번만 하고, 수신자마다 그 유저의 열린 소켓 전부에 보낸다
     */
    private void sendToReceivers(Map<String, Object> frame, List<Long> receiveUserIds) {
        TextMessage textMessage;
        try {
            textMessage = new TextMessage(objectMapper.writeValueAsString(frame));
        } catch (IOException e) {
            log.error("chat frame serialize error : {}", e.getMessage());
            return;
        }

        for (Long receiveUserId : receiveUserIds) {
            for (WebSocketSession session : getSessions(String.valueOf(receiveUserId))) {
                send(session, textMessage);
            }
        }
    }

    /**
     * 하트비트 : 열린 소켓 전부에 ping 을 보내고, timeout 동안 아무 프레임도 오지 않은 소켓은 닫는다 (ChatHeartbeatScheduler)
     */
    public void heartbeat(Duration timeout) {
        long now = clock.millis();
        for (Map<String, WebSocketSession> userSessions : webSocketSessionMap.values()) {
            for (WebSocketSession session : userSessions.values()) {
                long lastSeenAt = lastSeenAtBySession.getOrDefault(session.getId(), now);
                if (now - lastSeenAt > timeout.toMillis()) {
                    log.info("heartbeat timeout session : {}, silent {}ms", session.getId(), now - lastSeenAt);
                    closeQuietly(session);
                    continue;
                }
                send(session, PING);
            }
        }
    }

    /**
     * 보내다 실패한 소켓은 믿을 수 없으므로 닫는다 → 클라이언트가 다시 붙고 빈틈을 채운다.
     * 메모리 / Redis 정리는 닫힌 뒤 afterConnectionClosed 가 한다
     */
    private void send(WebSocketSession session, TextMessage textMessage) {
        if (!session.isOpen()) {
            return;
        }
        try {
            session.sendMessage(textMessage);
        } catch (IOException | RuntimeException e) {
            // RuntimeException : ConcurrentWebSocketSessionDecorator 의 보내기 시간 / 버퍼 한도 초과 등
            log.warn("send fail session : {}, error : {}", session.getId(), e.getMessage());
            closeQuietly(session);
        }
    }

    private static void closeQuietly(WebSocketSession session) {
        try {
            session.close(CloseStatus.SESSION_NOT_RELIABLE);
        } catch (IOException | RuntimeException e) {
            log.debug("close fail session : {}, error : {}", session.getId(), e.getMessage());
        }
    }

    /**
     * 이 서버에 붙어 있는 유저의 기기(소켓) 목록
     */
    public Collection<WebSocketSession> getSessions(String userId) {
        Map<String, WebSocketSession> userSessions = this.webSocketSessionMap.get(userId);
        return userSessions == null ? List.of() : userSessions.values();
    }
}
