package com.distributed.chat.system.common.event;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.json.JsonMapper;

/**
 * 서버 사이를 오가는 이벤트(이 패키지의 record)를 JSON 문자열로 바꾸고 읽는 한 곳.
 * Kafka 값은 문자열(StringSerializer / StringDeserializer)이고 그 내용이 이 JSON 이다 → kafka-ui 에서 그대로 읽힌다.
 * 서버간 HTTP(Feign)는 Spring 기본 JSON(application/json)으로 같은 record 를 주고받는다.
 * <p>
 * <b>호환 규칙</b> (Kafka 에 남아 있는 이벤트, 먼저 / 나중에 배포된 서버가 섞여도 읽히도록)
 * <ul>
 *   <li>필드는 <b>추가만</b> 한다. 이름을 바꾸거나 지우지 않는다 (바꿔야 하면 새 필드를 더하고 예전 필드는 남겨 둔다)</li>
 *   <li>모르는 필드는 무시한다 (record 의 {@code @JsonIgnoreProperties(ignoreUnknown = true)} + 이 매퍼 설정).
 *       새 서버가 필드를 더해 보내도 예전 서버가 그대로 읽는다</li>
 *   <li>없는 필드는 기본값 : 숫자 0, 문자열 "", 목록 빈 목록, 하위 이벤트 빈 값 (예전 바이너리 형식의 기본값과 같다).
 *       그래서 받는 쪽은 null 을 확인하지 않는다. 단, 모르는 enum 값은 null (받는 쪽이 버린다)</li>
 *   <li>모양부터 틀린 JSON(문법 오류, 객체가 아님, 빈 값)은 {@link MalformedEventException}. 다시 시도해도 같으므로 재시도하지 않는다</li>
 * </ul>
 */
public final class EventJson {

    private static final ObjectMapper MAPPER = JsonMapper.builder()
        .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
        .enable(DeserializationFeature.FAIL_ON_TRAILING_TOKENS)
        .build();

    private EventJson() {
    }

    public static String write(Object event) {
        try {
            return MAPPER.writeValueAsString(event);
        } catch (JsonProcessingException e) {
            // record 의 단순한 값만 쓰므로 일어나지 않는다
            throw new IllegalStateException("event serialize fail : " + event.getClass().getSimpleName(), e);
        }
    }

    /**
     * @throws MalformedEventException JSON 이 아니거나 그 이벤트 모양이 아님 (null / 빈 문자열 포함)
     */
    public static <T> T read(String payload, Class<T> type) {
        if (payload == null || payload.isBlank()) {
            throw new MalformedEventException(type.getSimpleName() + " : empty payload");
        }
        T event;
        try {
            event = MAPPER.readValue(payload, type);
        } catch (JsonProcessingException e) {
            throw new MalformedEventException(type.getSimpleName() + " : " + e.getOriginalMessage(), e);
        }
        if (event == null) {
            // "null" 리터럴
            throw new MalformedEventException(type.getSimpleName() + " : null payload");
        }
        return event;
    }
}
