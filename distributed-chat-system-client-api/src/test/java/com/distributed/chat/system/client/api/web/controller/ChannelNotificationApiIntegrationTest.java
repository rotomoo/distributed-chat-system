package com.distributed.chat.system.client.api.web.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.distributed.chat.system.mysql.entity.User;
import com.distributed.chat.system.mysql.pk.ChannelUserPk;
import com.distributed.chat.system.mysql.repository.ChannelUserRepository;
import com.distributed.chat.system.mysql.repository.UserRepository;
import com.jayway.jsonpath.JsonPath;
import jakarta.transaction.Transactional;
import java.util.UUID;
import org.hamcrest.Matchers;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

@DisplayName("[통합] 채널 알림 음소거 API 테스트")
// 테스트 JVM 이 실행 중인 스택의 Eureka 에 등록되면 게이트웨이가 실제 요청을 테스트로 보낼 수 있으므로 끈다
@SpringBootTest(properties = "eureka.client.enabled=false")
@AutoConfigureMockMvc
@Transactional
class ChannelNotificationApiIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private ChannelUserRepository channelUserRepository;

    private Long alice;
    private Long bob;
    private Long carol;

    @BeforeEach
    void setup() {
        alice = saveUser("alice");
        bob = saveUser("bob");
        carol = saveUser("carol");
    }

    @Test
    @DisplayName("success case : 채널 알림을 끄면 MySQL channel_user.notification_muted 에 저장되고 채널 목록에 muted 로 보이며 다시 켤 수 있다")
    void 채널_알림을_끄면_저장되고_채널_목록에_muted_로_보이며_다시_켤_수_있다() throws Exception {
        // given
        Long teamId = createTeam(alice);
        Long generalId = channelId(alice, teamId, "general");
        mockMvc.perform(as(alice, get("/v1/private/api/channel/channels").param("teamId", String.valueOf(teamId))))
            .andExpect(jsonPath("$.data.list[0].muted").value(false));

        // when
        mockMvc.perform(as(alice, setMuted(generalId, true)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.channelId").value(generalId))
            .andExpect(jsonPath("$.data.muted").value(true));

        // then
        assertThat(channelUserRepository.findById(new ChannelUserPk(generalId, alice)).orElseThrow().isNotificationMuted()).isTrue();
        mockMvc.perform(as(alice, get("/v1/private/api/channel/channels").param("teamId", String.valueOf(teamId))))
            .andExpect(jsonPath("$.data.list[0].muted").value(true));

        mockMvc.perform(as(alice, setMuted(generalId, false))).andExpect(status().isOk());
        mockMvc.perform(as(alice, get("/v1/private/api/channel/channels").param("teamId", String.valueOf(teamId))))
            .andExpect(jsonPath("$.data.list[0].muted").value(false));
    }

    @Test
    @DisplayName("success case : 음소거는 사람마다 따로다 (DM 도 같은 API), 같은 값을 다시 보내도 성공한다")
    void 음소거는_사람마다_따로다_DM_도_같은_API() throws Exception {
        // given
        Long teamId = createTeam(alice);
        mockMvc.perform(as(bob, post("/v1/private/api/team/" + teamId + "/join"))).andExpect(status().isOk());
        Long dmId = openDm(alice, teamId, bob);

        // when
        mockMvc.perform(as(alice, setMuted(dmId, true))).andExpect(status().isOk());
        mockMvc.perform(as(alice, setMuted(dmId, true))).andExpect(status().isOk());

        // then
        mockMvc.perform(as(alice, get("/v1/private/api/channel/dms").param("teamId", String.valueOf(teamId))))
            .andExpect(jsonPath("$.data.list[?(@.channelId == " + dmId + ")].muted").value(Matchers.contains(true)));
        mockMvc.perform(as(bob, get("/v1/private/api/channel/dms").param("teamId", String.valueOf(teamId))))
            .andExpect(jsonPath("$.data.list[?(@.channelId == " + dmId + ")].muted").value(Matchers.contains(false)));
    }

    @Test
    @DisplayName("fail case : 채널 멤버가 아니면 알림 설정을 바꿀 수 없다 (3003)")
    void 채널_멤버가_아니면_알림_설정을_바꿀_수_없다() throws Exception {
        // given
        Long teamId = createTeam(alice);
        Long generalId = channelId(alice, teamId, "general");

        // when & then
        mockMvc.perform(as(carol, setMuted(generalId, true)))
            .andExpect(status().isForbidden())
            .andExpect(jsonPath("$.code").value(3003));
        assertThat(channelUserRepository.findById(new ChannelUserPk(generalId, carol))).isEmpty();
    }

    @Test
    @DisplayName("fail case : muted 가 없으면 400")
    void muted_가_없으면_400() throws Exception {
        // given
        Long teamId = createTeam(alice);
        Long generalId = channelId(alice, teamId, "general");

        // when & then
        mockMvc.perform(as(alice, put("/v1/private/api/channel/" + generalId + "/notification")
                .contentType(MediaType.APPLICATION_JSON).content("{}")))
            .andExpect(status().isBadRequest());
    }

    private MockHttpServletRequestBuilder setMuted(Long channelId, boolean muted) {
        return put("/v1/private/api/channel/" + channelId + "/notification")
            .contentType(MediaType.APPLICATION_JSON).content("{\"muted\":" + muted + "}");
    }

    private Long createTeam(Long userId) throws Exception {
        String body = mockMvc.perform(as(userId, post("/v1/private/api/team/team").contentType(MediaType.APPLICATION_JSON)
                .content("{\"teamName\":\"team_" + UUID.randomUUID().toString().substring(0, 8) + "\"}")))
            .andExpect(status().isOk())
            .andReturn().getResponse().getContentAsString();
        return ((Number) JsonPath.read(body, "$.data.teamId")).longValue();
    }

    private Long channelId(Long userId, Long teamId, String channelName) throws Exception {
        String body = mockMvc.perform(as(userId, get("/v1/private/api/channel/channels").param("teamId", String.valueOf(teamId))))
            .andReturn().getResponse().getContentAsString();
        return ((Number) JsonPath.<java.util.List<Object>>read(body, "$.data.list[?(@.channelName == '" + channelName + "')].channelId")
            .get(0)).longValue();
    }

    private Long openDm(Long userId, Long teamId, Long targetUserId) throws Exception {
        String body = mockMvc.perform(as(userId, post("/v1/private/api/channel/dm").param("teamId", String.valueOf(teamId))
                .contentType(MediaType.APPLICATION_JSON).content("{\"userId\":" + targetUserId + "}")))
            .andExpect(status().isOk())
            .andReturn().getResponse().getContentAsString();
        return ((Number) JsonPath.read(body, "$.data.channelId")).longValue();
    }

    private MockHttpServletRequestBuilder as(Long userId, MockHttpServletRequestBuilder request) {
        return request.with(user(String.valueOf(userId)));
    }

    private Long saveUser(String name) {
        String account = name + "_" + UUID.randomUUID().toString().substring(0, 8);
        return userRepository.save(User.builder().userName(account).account(account).password("hash").build()).getId();
    }
}
