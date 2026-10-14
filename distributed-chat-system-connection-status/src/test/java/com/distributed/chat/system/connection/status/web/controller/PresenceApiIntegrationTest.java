package com.distributed.chat.system.connection.status.web.controller;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.distributed.chat.system.common.redis.ChatPresenceKeys;
import com.distributed.chat.system.mysql.entity.Team;
import com.distributed.chat.system.mysql.entity.TeamUser;
import com.distributed.chat.system.mysql.entity.User;
import com.distributed.chat.system.mysql.repository.TeamRepository;
import com.distributed.chat.system.mysql.repository.TeamUserRepository;
import com.distributed.chat.system.mysql.repository.UserRepository;
import jakarta.servlet.http.Cookie;
import jakarta.transaction.Transactional;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.UUID;
import org.hamcrest.Matchers;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextImpl;
import org.springframework.session.Session;
import org.springframework.session.SessionRepository;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

/**
 * 진짜 MySQL / Redis (로컬 docker 스택). MySQL 은 트랜잭션 롤백, Redis 키는 테스트가 만든 것만 지운다.
 * 접속 상태 키는 채팅 서버가 쓰는 모양 그대로(RedisTemplate JSON 직렬화) 넣는다
 */
@DisplayName("[통합] 팀 멤버 접속 상태 API 테스트")
// 테스트 JVM 이 실행 중인 스택의 Eureka 에 등록되면 게이트웨이가 실제 요청을 테스트로 보낼 수 있으므로 끈다
@SpringBootTest(properties = "eureka.client.enabled=false")
@AutoConfigureMockMvc
@Transactional
class PresenceApiIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private TeamRepository teamRepository;

    @Autowired
    private TeamUserRepository teamUserRepository;

    @Autowired
    private RedisTemplate<String, Object> redisTemplate;

    @Autowired
    @SuppressWarnings("rawtypes")
    private SessionRepository sessionRepository;

    private Long alice;
    private Long bob;
    private Long carol;
    private Long dave;
    private Long teamId;

    /**
     * Redis 는 트랜잭션 롤백이 안 되므로 테스트가 만든 것을 직접 지운다
     */
    private final List<String> redisKeys = new ArrayList<>();
    private final List<String> sessionIds = new ArrayList<>();

    @BeforeEach
    void setup() {
        alice = saveUser("alice");
        bob = saveUser("bob");
        carol = saveUser("carol");
        dave = saveUser("dave");
        teamId = teamRepository.save(Team.builder().teamName("team_" + UUID.randomUUID().toString().substring(0, 8))
            .deleteYn(false).build()).getId();
        List.of(alice, bob, carol).forEach(userId -> teamUserRepository.save(TeamUser.builder()
            .teamId(teamId).userId(userId).deleteYn(false).build()));
    }

    @AfterEach
    @SuppressWarnings("unchecked")
    void tearDown() {
        redisTemplate.delete(redisKeys);
        sessionIds.forEach(sessionRepository::deleteById);
    }

    @Test
    @DisplayName("success case : 팀 멤버는 멤버 전체의 online / away / offline 을 userId 순으로 받는다")
    void 팀_멤버는_멤버_전체의_online_away_offline_을_userId_순으로_받는다() throws Exception {
        // given : alice online, bob away, carol 키 없음 (offline), dave 는 팀 밖이라 online 이어도 안 나온다
        givenPresence(alice, "online");
        givenPresence(bob, "away");
        givenPresence(dave, "online");

        // when & then
        mockMvc.perform(as(alice, get("/v1/private/api/presence").param("teamId", String.valueOf(teamId))))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.success").value(true))
            .andExpect(jsonPath("$.code").value(0))
            .andExpect(jsonPath("$.data.list.length()").value(3))
            .andExpect(jsonPath("$.data.list[*].userId").value(Matchers.contains(alice.intValue(), bob.intValue(), carol.intValue())))
            .andExpect(jsonPath("$.data.list[*].status").value(Matchers.contains("online", "away", "offline")));
    }

    @Test
    @DisplayName("success case : 삭제된 팀 멤버는 목록에서 빠진다")
    void 삭제된_팀_멤버는_목록에서_빠진다() throws Exception {
        // given
        teamUserRepository.save(TeamUser.builder().teamId(teamId).userId(dave).deleteYn(true).build());
        givenPresence(dave, "online");

        // when & then
        mockMvc.perform(as(alice, get("/v1/private/api/presence").param("teamId", String.valueOf(teamId))))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.list[?(@.userId == " + dave + ")]").isEmpty());
    }

    @Test
    @DisplayName("fail case : 팀 멤버가 아니면 403 / 2003 이다")
    void 팀_멤버가_아니면_403_2003_이다() throws Exception {
        // when & then
        mockMvc.perform(as(dave, get("/v1/private/api/presence").param("teamId", String.valueOf(teamId))))
            .andExpect(status().isForbidden())
            .andExpect(jsonPath("$.code").value(2003))
            .andExpect(jsonPath("$.data.errorCodeName").value("NOT_TEAM_MEMBER"))
            .andExpect(jsonPath("$.success").value(false));
    }

    @Test
    @DisplayName("fail case : 로그인하지 않으면 401 / 1003 이고 세션을 만들지 않는다")
    void 로그인하지_않으면_401_1003_이고_세션을_만들지_않는다() throws Exception {
        // when & then
        mockMvc.perform(get("/v1/private/api/presence").param("teamId", String.valueOf(teamId)))
            .andExpect(status().isUnauthorized())
            .andExpect(jsonPath("$.code").value(1003))
            .andExpect(jsonPath("$.data.errorCodeName").value("NOT_EXIST_USER_INFO"))
            .andExpect(header().doesNotExist("Set-Cookie"));
    }

    @Test
    @DisplayName("fail case : 없거나 만료된 SESSION 쿠키도 401 / 1003 이다")
    void 없거나_만료된_SESSION_쿠키도_401_1003_이다() throws Exception {
        // when & then
        mockMvc.perform(get("/v1/private/api/presence").param("teamId", String.valueOf(teamId))
                .cookie(sessionCookie(UUID.randomUUID().toString())))
            .andExpect(status().isUnauthorized())
            .andExpect(jsonPath("$.code").value(1003));
    }

    @Test
    @DisplayName("success case : client-api 가 로그인할 때 Redis 에 저장한 세션(SESSION 쿠키) 그대로 조회할 수 있다")
    void client_api_가_저장한_로그인_세션_그대로_조회할_수_있다() throws Exception {
        // given : client-api 로그인과 같은 모양 (principal = 스프링 시큐리티 User, 이름 = userId)
        String sessionId = saveLoginSession(alice);
        givenPresence(bob, "away");

        // when & then
        mockMvc.perform(get("/v1/private/api/presence").param("teamId", String.valueOf(teamId)).cookie(sessionCookie(sessionId)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.list[?(@.userId == " + bob + ")].status").value(Matchers.contains("away")));
    }

    @Test
    @DisplayName("fail case : teamId 가 없으면 400 이다")
    void teamId_가_없으면_400_이다() throws Exception {
        // when & then
        mockMvc.perform(as(alice, get("/v1/private/api/presence")))
            .andExpect(status().isBadRequest());
    }

    private void givenPresence(Long userId, String presence) {
        String key = ChatPresenceKeys.presenceKey(userId);
        redisKeys.add(key);
        redisTemplate.opsForValue().set(key, presence, ChatPresenceKeys.PRESENCE_TTL);
    }

    @SuppressWarnings("unchecked")
    private String saveLoginSession(Long userId) {
        Session session = sessionRepository.createSession();
        org.springframework.security.core.userdetails.User principal =
            new org.springframework.security.core.userdetails.User(String.valueOf(userId), "hash", List.of());
        principal.eraseCredentials();
        session.setAttribute("userId", String.valueOf(userId));
        session.setAttribute("SPRING_SECURITY_CONTEXT", new SecurityContextImpl(
            UsernamePasswordAuthenticationToken.authenticated(principal, null, principal.getAuthorities())));
        sessionRepository.save(session);
        sessionIds.add(session.getId());
        return session.getId();
    }

    private static Cookie sessionCookie(String sessionId) {
        return new Cookie("SESSION", Base64.getEncoder().encodeToString(sessionId.getBytes(StandardCharsets.UTF_8)));
    }

    private MockHttpServletRequestBuilder as(Long userId, MockHttpServletRequestBuilder request) {
        return request.with(user(String.valueOf(userId)));
    }

    private Long saveUser(String name) {
        String account = name + "_" + UUID.randomUUID().toString().substring(0, 8);
        return userRepository.save(User.builder().userName(account).account(account).password("hash").build()).getId();
    }
}
