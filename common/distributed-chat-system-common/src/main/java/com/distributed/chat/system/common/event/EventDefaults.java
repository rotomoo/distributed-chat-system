package com.distributed.chat.system.common.event;

import java.util.List;

/**
 * 이벤트 record 의 빈 값 채우기 (없는 필드 → "" / 빈 목록). 받는 쪽이 null 을 확인하지 않아도 되게 한다
 */
final class EventDefaults {

    private EventDefaults() {
    }

    static String text(String value) {
        return value == null ? "" : value;
    }

    /**
     * 바꿀 수 없는 복사본. 원소에 null 이 있으면 NullPointerException (JSON 이면 읽기 실패 → {@link MalformedEventException})
     */
    static <T> List<T> list(List<T> values) {
        return values == null ? List.of() : List.copyOf(values);
    }
}
