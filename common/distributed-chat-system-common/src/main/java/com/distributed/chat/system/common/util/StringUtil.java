package com.distributed.chat.system.common.util;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import java.security.SecureRandom;
import java.util.Map;

public class StringUtil {

    /**
     * salt 생성
     *
     * @return
     */
    public static String createSalt() {

        // 난수 생성 알고리즘 기본값 'SHA1PRNG' (== SecureRandom.getInstance("SHA1PRNG"))
        SecureRandom random = new SecureRandom();
        byte[] bytes = new byte[16];

        // nextBytes 메서드를 호출하여 bytes의 크기만큼 난수로 채움.
        random.nextBytes(bytes);

        // 16진수 문자열로 변환
        StringBuilder sb = new StringBuilder();

        for (byte b : bytes) {
            // byte b를 2자리의 16진수 형식으로 변환한다. ex) b = 10 -> 0a
            String format = String.format("%02x", b);

            sb.append(format);
        }
        return sb.toString();

//        // Base 64 인코딩 필요시 사용
//        return new String(Base64.getEncoder().encode(bytes));
    }

    public static Map<String, Object> toMap(Object obj) {
        ObjectMapper objectMapper = new ObjectMapper();

        // LocalDateTime Casting
        objectMapper.registerModule(new JavaTimeModule());

        return objectMapper.convertValue(obj, Map.class);
    }
}
