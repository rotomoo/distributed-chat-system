package com.distributed.chat.system.client.api.web.controller;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.distributed.chat.system.mysql.entity.User;
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

@DisplayName("[통합] 팀 / 채널 / DM API 테스트")
// 테스트 JVM 이 실행 중인 스택의 Eureka 에 등록되면 게이트웨이가 실제 요청을 테스트로 보낼 수 있으므로 끈다
@SpringBootTest(properties = "eureka.client.enabled=false")
@AutoConfigureMockMvc
@Transactional
class ChannelApiIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UserRepository userRepository;

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
    @DisplayName("success case : 팀을 만들면 내 팀 목록에 보이고 general 채널에 참여된 상태다")
    void 팀을_만들면_내_팀_목록에_보이고_general_채널에_참여된_상태다() throws Exception {
        // given
        String teamName = uniqueTeamName();

        // when
        Long teamId = createTeam(alice, teamName);

        // then
        mockMvc.perform(as(alice, get("/v1/private/api/team/teams/me")))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.list[*].teamName", Matchers.hasItem(teamName)));
        mockMvc.perform(as(alice, get("/v1/private/api/channel/channels").param("teamId", String.valueOf(teamId))))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.list[0].channelName").value("general"))
            .andExpect(jsonPath("$.data.list[0].joined").value(true));
    }

    @Test
    @DisplayName("success case : 팀에 가입하면 general 에만 참여되고 다른 공개 채널은 직접 참여해야 한다")
    void 팀에_가입하면_general_에만_참여되고_다른_공개_채널은_직접_참여해야_한다() throws Exception {
        // given
        Long teamId = createTeam(alice, uniqueTeamName());
        Long randomId = createChannel(alice, teamId, "random");

        // when
        mockMvc.perform(as(bob, post("/v1/private/api/team/" + teamId + "/join")))
            .andExpect(status().isOk());

        // then
        mockMvc.perform(as(bob, get("/v1/private/api/channel/channels").param("teamId", String.valueOf(teamId))))
            .andExpect(jsonPath("$.data.list[?(@.channelName == 'general')].joined").value(Matchers.contains(true)))
            .andExpect(jsonPath("$.data.list[?(@.channelName == 'random')].joined").value(Matchers.contains(false)));

        mockMvc.perform(as(bob, post("/v1/private/api/channel/" + randomId + "/join")))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.joined").value(true));
        mockMvc.perform(as(bob, get("/v1/private/api/channel/channels").param("teamId", String.valueOf(teamId))))
            .andExpect(jsonPath("$.data.list[?(@.channelName == 'random')].joined").value(Matchers.contains(true)));
    }

    @Test
    @DisplayName("fail case : 팀 멤버가 아니면 채널 목록을 볼 수 없다")
    void 팀_멤버가_아니면_채널_목록을_볼_수_없다() throws Exception {
        // given
        Long teamId = createTeam(alice, uniqueTeamName());

        // when & then
        mockMvc.perform(as(carol, get("/v1/private/api/channel/channels").param("teamId", String.valueOf(teamId))))
            .andExpect(status().isForbidden())
            .andExpect(jsonPath("$.code").value(2003));
    }

    @Test
    @DisplayName("fail case : 같은 팀에 같은 이름의 채널은 만들 수 없다")
    void 같은_팀에_같은_이름의_채널은_만들_수_없다() throws Exception {
        // given
        Long teamId = createTeam(alice, uniqueTeamName());

        // when & then
        mockMvc.perform(as(alice, post("/v1/private/api/channel/channel").param("teamId", String.valueOf(teamId))
                .contentType(MediaType.APPLICATION_JSON).content("{\"channelName\":\"general\"}")))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value(3001));
    }

    @Test
    @DisplayName("success case : DM 을 두 번 열어도 같은 채널이고 상대방 DM 목록에도 보인다")
    void DM_을_두_번_열어도_같은_채널이고_상대방_DM_목록에도_보인다() throws Exception {
        // given
        Long teamId = createTeam(alice, uniqueTeamName());
        mockMvc.perform(as(bob, post("/v1/private/api/team/" + teamId + "/join")));

        // when
        Long first = openDm(alice, teamId, bob);
        Long second = openDm(bob, teamId, alice);

        // then
        org.assertj.core.api.Assertions.assertThat(second).isEqualTo(first);
        mockMvc.perform(as(bob, get("/v1/private/api/channel/dms").param("teamId", String.valueOf(teamId))))
            .andExpect(jsonPath("$.data.list.length()").value(1))
            .andExpect(jsonPath("$.data.list[0].channelId").value(first.intValue()))
            .andExpect(jsonPath("$.data.list[0].userId").value(alice.intValue()));
    }

    @Test
    @DisplayName("fail case : 팀 멤버가 아닌 사람과는 DM 을 열 수 없다")
    void 팀_멤버가_아닌_사람과는_DM_을_열_수_없다() throws Exception {
        // given
        Long teamId = createTeam(alice, uniqueTeamName());

        // when & then
        mockMvc.perform(as(alice, post("/v1/private/api/channel/dm").param("teamId", String.valueOf(teamId))
                .contentType(MediaType.APPLICATION_JSON).content("{\"userId\":" + carol + "}")))
            .andExpect(status().isForbidden())
            .andExpect(jsonPath("$.code").value(2003));
    }

    private Long createTeam(Long userId, String teamName) throws Exception {
        String body = mockMvc.perform(as(userId, post("/v1/private/api/team/team")
                .contentType(MediaType.APPLICATION_JSON).content("{\"teamName\":\"" + teamName + "\"}")))
            .andExpect(status().isOk())
            .andReturn().getResponse().getContentAsString();
        return ((Number) JsonPath.read(body, "$.data.teamId")).longValue();
    }

    private Long createChannel(Long userId, Long teamId, String channelName) throws Exception {
        String body = mockMvc.perform(as(userId, post("/v1/private/api/channel/channel").param("teamId", String.valueOf(teamId))
                .contentType(MediaType.APPLICATION_JSON).content("{\"channelName\":\"" + channelName + "\"}")))
            .andExpect(status().isOk())
            .andReturn().getResponse().getContentAsString();
        return ((Number) JsonPath.read(body, "$.data.channelId")).longValue();
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

    private String uniqueTeamName() {
        return "team_" + UUID.randomUUID().toString().substring(0, 8);
    }
}
