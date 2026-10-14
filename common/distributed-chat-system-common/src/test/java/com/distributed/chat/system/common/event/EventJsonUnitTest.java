package com.distributed.chat.system.common.event;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.distributed.chat.system.common.event.ChatMessageDelivery.Attachment;
import com.distributed.chat.system.common.event.ChatReactionDelivery.Reaction;
import com.distributed.chat.system.common.event.ChatReactionEvent.Action;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.charset.StandardCharsets;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("[단위] EventJson 테스트")
class EventJsonUnitTest {

    private static final ChatMessageEvent MESSAGE = new ChatMessageEvent("event-1", 5L, 7L, "점심 뭐 먹을까요? 😀", 1_700_000_000_000L,
        List.of("6acc66675f99715e286f0f6e"));

    // ── 왕복 (쓰고 다시 읽으면 같다) ──────────────────────────────

    @Test
    @DisplayName("success case : 메시지 이벤트를 JSON 으로 쓰고 다시 읽으면 같다")
    void 메시지_이벤트를_JSON_으로_쓰고_다시_읽으면_같다() {
        // act
        ChatMessageEvent read = EventJson.read(EventJson.write(MESSAGE), ChatMessageEvent.class);

        // assert
        assertThat(read).isEqualTo(MESSAGE);
    }

    @Test
    @DisplayName("success case : 리액션 이벤트(ADD / REMOVE)를 JSON 으로 쓰고 다시 읽으면 같다")
    void 리액션_이벤트를_JSON_으로_쓰고_다시_읽으면_같다() {
        // arrange
        ChatReactionEvent add = new ChatReactionEvent(5L, 7L, "6acc66675f99715e286f0f6e", "👍", Action.ADD, 1L);
        ChatReactionEvent remove = new ChatReactionEvent(5L, 7L, "6acc66675f99715e286f0f6e", "🎉", Action.REMOVE, 2L);

        // act
        ChatReactionEvent readAdd = EventJson.read(EventJson.write(add), ChatReactionEvent.class);
        ChatReactionEvent readRemove = EventJson.read(EventJson.write(remove), ChatReactionEvent.class);

        // assert
        assertThat(readAdd).isEqualTo(add);
        assertThat(readRemove).isEqualTo(remove);
    }

    @Test
    @DisplayName("success case : 메시지 전달 이벤트(메시지, 수신자, 첨부 포함)를 JSON 으로 쓰고 다시 읽으면 같다")
    void 메시지_전달_이벤트를_JSON_으로_쓰고_다시_읽으면_같다() {
        // arrange
        ChatMessageDelivery delivery = new ChatMessageDelivery("6acc66675f99715e286f0f70", MESSAGE, List.of(5L, 17L),
            List.of(new Attachment("6acc66675f99715e286f0f6e", "사진.png", "image/png", 10L)));

        // act
        ChatMessageDelivery read = EventJson.read(EventJson.write(delivery), ChatMessageDelivery.class);

        // assert
        assertThat(read).isEqualTo(delivery);
    }

    @Test
    @DisplayName("success case : 리액션 전달 이벤트를 JSON 으로 쓰고 다시 읽으면 같다")
    void 리액션_전달_이벤트를_JSON_으로_쓰고_다시_읽으면_같다() {
        // arrange
        ChatReactionDelivery delivery = new ChatReactionDelivery("6acc66675f99715e286f0f70", 7L,
            List.of(new Reaction("👍", List.of(5L, 17L)), new Reaction("🎉", List.of(9L))), List.of(5L, 17L));

        // act
        ChatReactionDelivery read = EventJson.read(EventJson.write(delivery), ChatReactionDelivery.class);

        // assert
        assertThat(read).isEqualTo(delivery);
    }

    @Test
    @DisplayName("success case : 알림 이벤트를 JSON 으로 쓰고 다시 읽으면 같다")
    void 알림_이벤트를_JSON_으로_쓰고_다시_읽으면_같다() {
        // arrange
        ChatNotificationEvent notification = new ChatNotificationEvent("6acc66675f99715e286f0f70", 7L, 5L, "점심 뭐 먹을까요?", 2,
            1_700_000_000_000L, List.of(9L, 17L));

        // act
        ChatNotificationEvent read = EventJson.read(EventJson.write(notification), ChatNotificationEvent.class);

        // assert
        assertThat(read).isEqualTo(notification);
    }

    @Test
    @DisplayName("success case : 필드 이름은 camelCase 로 고정이고 kafka-ui 에서 그대로 읽히는 JSON 이다 (이름을 바꾸면 여기서 걸린다)")
    void 필드_이름은_camelCase_로_고정이다() {
        // act
        String message = EventJson.write(MESSAGE);
        String reaction = EventJson.write(new ChatReactionEvent(5L, 7L, "m", "👍", Action.REMOVE, 1L));
        String notification = EventJson.write(new ChatNotificationEvent("m", 7L, 5L, "p", 2, 1L, List.of(9L)));

        // assert
        assertThat(message).isEqualTo("{\"eventId\":\"event-1\",\"sendUserId\":5,\"channelId\":7,\"message\":\"점심 뭐 먹을까요? 😀\","
            + "\"publishDt\":1700000000000,\"attachmentFileIds\":[\"6acc66675f99715e286f0f6e\"]}");
        assertThat(reaction).isEqualTo("{\"sendUserId\":5,\"channelId\":7,\"messageId\":\"m\",\"emoji\":\"👍\",\"action\":\"REMOVE\",\"publishDt\":1}");
        assertThat(notification).isEqualTo("{\"messageId\":\"m\",\"channelId\":7,\"senderId\":5,\"preview\":\"p\",\"attachmentCount\":2,"
            + "\"publishDt\":1,\"receiverUserIds\":[9]}");
    }

    // ── 호환 : 모르는 필드 / 없는 필드 ─────────────────────────────

    @Test
    @DisplayName("success case : 모르는 필드(새 서버가 더한 필드)는 무시하고 아는 필드만 읽는다")
    void 모르는_필드는_무시하고_아는_필드만_읽는다() {
        // arrange : 최상위, 하위 이벤트, 목록 안 객체에 모두 모르는 필드
        String json = "{\"messageId\":\"m\",\"newTopLevel\":{\"a\":[1,2]},"
            + "\"chatMessage\":{\"eventId\":\"e\",\"sendUserId\":5,\"channelId\":7,\"message\":\"hi\",\"threadId\":\"t-1\"},"
            + "\"receiveUserIds\":[5],\"attachments\":[{\"fileId\":\"f\",\"name\":\"a.png\",\"contentType\":\"image/png\",\"size\":3,\"width\":640}]}";

        // act
        ChatMessageDelivery delivery = EventJson.read(json, ChatMessageDelivery.class);

        // assert
        assertThat(delivery.messageId()).isEqualTo("m");
        assertThat(delivery.chatMessage()).isEqualTo(new ChatMessageEvent("e", 5L, 7L, "hi", 0L, List.of()));
        assertThat(delivery.receiveUserIds()).containsExactly(5L);
        assertThat(delivery.attachments()).containsExactly(new Attachment("f", "a.png", "image/png", 3L));
    }

    @Test
    @DisplayName("success case : 모르는 필드 무시는 record 에 붙어 있어 Spring / Feign 기본 ObjectMapper(모르는 필드면 실패하는 설정)로도 같다")
    void 모르는_필드_무시는_기본_ObjectMapper_로도_같다() throws Exception {
        // arrange : 기본 설정 ObjectMapper 는 FAIL_ON_UNKNOWN_PROPERTIES = true
        ObjectMapper strict = new ObjectMapper();
        String json = "{\"sendUserId\":5,\"channelId\":7,\"messageId\":\"m\",\"emoji\":\"👍\",\"action\":\"ADD\",\"publishDt\":1,\"newField\":true}";

        // act
        ChatReactionEvent reaction = strict.readValue(json, ChatReactionEvent.class);
        ChatNotificationEvent notification = strict.readValue("{\"messageId\":\"m\",\"channelId\":7,\"x\":1}", ChatNotificationEvent.class);
        ChatReactionDelivery delivery = strict.readValue("{\"reactions\":[{\"emoji\":\"👍\",\"userIds\":[1],\"x\":1}],\"x\":1}",
            ChatReactionDelivery.class);
        ChatMessageDelivery messageDelivery = strict.readValue("{\"x\":1,\"chatMessage\":{\"message\":\"hi\",\"x\":1},"
            + "\"attachments\":[{\"fileId\":\"f\",\"x\":1}]}", ChatMessageDelivery.class);

        // assert
        assertThat(reaction).isEqualTo(new ChatReactionEvent(5L, 7L, "m", "👍", Action.ADD, 1L));
        assertThat(notification.channelId()).isEqualTo(7L);
        assertThat(delivery.reactions()).containsExactly(new Reaction("👍", List.of(1L)));
        assertThat(messageDelivery.chatMessage().message()).isEqualTo("hi");
        assertThat(messageDelivery.attachments()).containsExactly(new Attachment("f", "", "", 0L));
    }

    @Test
    @DisplayName("success case : 없는 필드는 기본값 (숫자 0, 문자열 \"\", 목록 빈 목록, 하위 이벤트 빈 값) 이라 받는 쪽이 null 을 보지 않는다")
    void 없는_필드는_기본값이다() {
        // act
        ChatMessageEvent message = EventJson.read("{}", ChatMessageEvent.class);
        ChatReactionEvent reaction = EventJson.read("{}", ChatReactionEvent.class);
        ChatMessageDelivery delivery = EventJson.read("{\"attachments\":[{}]}", ChatMessageDelivery.class);
        ChatReactionDelivery reactionDelivery = EventJson.read("{\"reactions\":[{}],\"receiveUserIds\":null}", ChatReactionDelivery.class);
        ChatNotificationEvent notification = EventJson.read("{}", ChatNotificationEvent.class);

        // assert
        assertThat(message).isEqualTo(new ChatMessageEvent("", 0L, 0L, "", 0L, List.of()));
        assertThat(message).isEqualTo(ChatMessageEvent.empty());
        assertThat(reaction).isEqualTo(new ChatReactionEvent(0L, 0L, "", "", null, 0L));
        assertThat(delivery.messageId()).isEmpty();
        assertThat(delivery.chatMessage()).isEqualTo(ChatMessageEvent.empty());
        assertThat(delivery.receiveUserIds()).isEmpty();
        assertThat(delivery.attachments()).containsExactly(new Attachment("", "", "", 0L));
        assertThat(reactionDelivery.reactions()).containsExactly(new Reaction("", List.of()));
        assertThat(reactionDelivery.receiveUserIds()).isEmpty();
        assertThat(notification).isEqualTo(new ChatNotificationEvent("", 0L, 0L, "", 0, 0L, List.of()));
    }

    @Test
    @DisplayName("success case : 이벤트의 목록은 바꿀 수 없는 복사본이다 (넘긴 목록을 나중에 바꿔도 이벤트는 그대로)")
    void 이벤트의_목록은_바꿀_수_없는_복사본이다() {
        // arrange
        List<Long> receivers = new java.util.ArrayList<>(List.of(9L));

        // act
        ChatNotificationEvent notification = new ChatNotificationEvent("m", 7L, 5L, "p", 0, 1L, receivers);
        receivers.add(17L);

        // assert
        assertThat(notification.receiverUserIds()).containsExactly(9L);
        assertThatThrownBy(() -> notification.receiverUserIds().add(1L)).isInstanceOf(UnsupportedOperationException.class);
    }

    // ── action enum ─────────────────────────────────────────────

    @Test
    @DisplayName("success case : action 은 ADD / REMOVE 이름 그대로 읽고, 없거나 모르는 값이면 null (받는 쪽이 버린다)")
    void action_은_이름_그대로_읽고_없거나_모르는_값이면_null() throws Exception {
        // act
        Action add = EventJson.read("{\"action\":\"ADD\"}", ChatReactionEvent.class).action();
        Action remove = EventJson.read("{\"action\":\"REMOVE\"}", ChatReactionEvent.class).action();
        Action missing = EventJson.read("{}", ChatReactionEvent.class).action();
        Action unknown = EventJson.read("{\"action\":\"TOGGLE\"}", ChatReactionEvent.class).action();
        Action unknownWithStrictMapper = new ObjectMapper().readValue("{\"action\":\"TOGGLE\"}", ChatReactionEvent.class).action();

        // assert
        assertThat(add).isEqualTo(Action.ADD);
        assertThat(remove).isEqualTo(Action.REMOVE);
        assertThat(missing).isNull();
        assertThat(unknown).isNull();
        assertThat(unknownWithStrictMapper).isNull();
    }

    // ── 읽을 수 없는 값 ──────────────────────────────────────────

    @Test
    @DisplayName("fail case : JSON 이 아니거나 이벤트 모양이 아니면 MalformedEventException (예전 바이너리 값 포함)")
    void JSON_이_아니거나_이벤트_모양이_아니면_MalformedEventException() {
        // arrange : 예전 바이너리 형식 메시지(필드 1 = 5, 필드 2 = 7)를 문자열로 읽은 것
        String binary = new String(new byte[]{0x08, 0x05, 0x10, 0x07, 0x1a, 0x02, 'h', 'i'}, StandardCharsets.UTF_8);

        // act & assert
        for (String payload : List.of(binary, "{\"sendUserId\":", "[1,2]", "\"text\"", "123", "{\"sendUserId\":\"five\"}",
            "{\"attachmentFileIds\":[null]}", "{} trailing")) {
            assertThatThrownBy(() -> EventJson.read(payload, ChatMessageEvent.class))
                .as(payload)
                .isInstanceOf(MalformedEventException.class)
                .hasMessageStartingWith("ChatMessageEvent : ");
        }
    }

    @Test
    @DisplayName("fail case : null / 빈 문자열 / null 리터럴도 MalformedEventException 이다 (null 이벤트를 돌려주지 않는다)")
    void null_빈_문자열_null_리터럴도_MalformedEventException_이다() {
        // act & assert
        assertThatThrownBy(() -> EventJson.read(null, ChatNotificationEvent.class)).isInstanceOf(MalformedEventException.class);
        assertThatThrownBy(() -> EventJson.read(" ", ChatNotificationEvent.class)).isInstanceOf(MalformedEventException.class);
        assertThatThrownBy(() -> EventJson.read("null", ChatNotificationEvent.class)).isInstanceOf(MalformedEventException.class);
    }
}
