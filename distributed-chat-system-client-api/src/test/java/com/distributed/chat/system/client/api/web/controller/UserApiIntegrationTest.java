package com.distributed.chat.system.client.api.web.controller;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.distributed.chat.system.common.redis.ChatPresenceKeys;
import com.distributed.chat.system.mysql.entity.User;
import com.distributed.chat.system.mysql.repository.UserRepository;
import jakarta.transaction.Transactional;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.test.web.servlet.MockMvc;

@DisplayName("[통합] 내 정보 API 테스트")
// 테스트 JVM 이 실행 중인 스택의 Eureka 에 등록되면 게이트웨이가 실제 요청을 테스트로 보낼 수 있으므로 끈다
@SpringBootTest(properties = "eureka.client.enabled=false")
@AutoConfigureMockMvc
@Transactional
class UserApiIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private StringRedisTemplate stringRedisTemplate;

    private Long alice;

    @AfterEach
    void cleanup() {
        if (alice != null) {
            stringRedisTemplate.delete(ChatPresenceKeys.userKey(alice));
        }
    }

    @Test
    @DisplayName("success case : 채팅 서버에 접속해 Redis {userId} 가 있어도 내 정보를 돌려준다")
    void 채팅_서버에_접속해_Redis_접속_키가_있어도_내_정보를_돌려준다() throws Exception {
        // given : 채팅 서버가 쓰는 것과 같은 값
        alice = saveUser("alice");
        stringRedisTemplate.opsForValue().set(ChatPresenceKeys.userKey(alice),
            ChatPresenceKeys.userKeyValue("127.0.0.1:9999", ChatPresenceKeys.ONLINE), ChatPresenceKeys.TTL);

        // when & then
        mockMvc.perform(get("/v1/private/api/user/my-info").with(user(String.valueOf(alice))))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.userId").value(alice))
            .andExpect(jsonPath("$.data.chatServerUrl").doesNotExist());
    }

    private Long saveUser(String name) {
        String account = name + "_" + UUID.randomUUID().toString().substring(0, 8);
        return userRepository.save(User.builder().userName(account).account(account).password("hash").build()).getId();
    }
}
