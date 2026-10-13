package com.distributed.chat.system.client.api.web.controller;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.distributed.chat.system.client.api.base.cache.ChannelMemberCache;
import com.distributed.chat.system.mongodb.collection.ChatMessage;
import com.distributed.chat.system.mongodb.repository.ChatMessageRepository;
import com.distributed.chat.system.mysql.entity.User;
import com.distributed.chat.system.mysql.repository.UserRepository;
import com.jayway.jsonpath.JsonPath;
import jakarta.persistence.EntityManager;
import jakarta.transaction.Transactional;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.UUID;
import org.bson.types.ObjectId;
import org.hamcrest.Matchers;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.redis.core.RedisTemplate;
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

    @Autowired
    private ChatMessageRepository chatMessageRepository;

    @Autowired
    private RedisTemplate<String, Object> redisTemplate;

    @Autowired
    private EntityManager entityManager;

    private Long alice;
    private Long bob;
    private Long carol;

    /**
     * MongoDB / Redis 는 트랜잭션 롤백이 안 되므로 테스트가 만든 것을 직접 지운다
     */
    private final List<ChatMessage> savedMessages = new ArrayList<>();
    private final List<String> redisKeys = new ArrayList<>();

    @BeforeEach
    void setup() {
        alice = saveUser("alice");
        bob = saveUser("bob");
        carol = saveUser("carol");
    }

    @AfterEach
    void tearDown() {
        chatMessageRepository.deleteAll(savedMessages);
        redisTemplate.delete(redisKeys);
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

    @Test
    @DisplayName("success case : 채널 메시지를 최근 것부터 끊어서 가져오고 before 로 이전 메시지를 이어서 가져온다")
    void 채널_메시지를_최근_것부터_끊어서_가져오고_before_로_이전_메시지를_이어서_가져온다() throws Exception {
        // given
        Long teamId = createTeam(alice, uniqueTeamName());
        Long channelId = createChannel(alice, teamId, "history");
        for (int i = 1; i <= 3; i++) {
            chatMessageRepository.save(ChatMessage.builder().channelId(channelId).content("m" + i)
                .createUserId(alice).eventPublishDt(Instant.now()).createDt(Instant.now()).build());
        }

        // when
        String latest = mockMvc.perform(as(alice, get("/v1/private/api/channel/" + channelId + "/messages").param("size", "2")))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.list[*].message").value(Matchers.contains("m2", "m3")))
            .andExpect(jsonPath("$.data.hasNext").value(true))
            .andReturn().getResponse().getContentAsString();
        String oldestOfPage = JsonPath.read(latest, "$.data.list[0].messageId");

        // then
        mockMvc.perform(as(alice, get("/v1/private/api/channel/" + channelId + "/messages")
                .param("before", oldestOfPage).param("size", "2")))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.list[*].message").value(Matchers.contains("m1")))
            .andExpect(jsonPath("$.data.hasNext").value(false));

        chatMessageRepository.deleteAll(chatMessageRepository.findLatestMessages(channelId, 10).getContent());
    }

    @Test
    @DisplayName("fail case : 채널 멤버가 아니면 메시지를 볼 수 없다")
    void 채널_멤버가_아니면_메시지를_볼_수_없다() throws Exception {
        // given
        Long teamId = createTeam(alice, uniqueTeamName());
        Long channelId = createChannel(alice, teamId, "secret");

        // when & then
        mockMvc.perform(as(bob, get("/v1/private/api/channel/" + channelId + "/messages")))
            .andExpect(status().isForbidden())
            .andExpect(jsonPath("$.code").value(3003));
    }

    @Test
    @DisplayName("success case : 채널 멤버 캐시가 비면 내부 API 로 MySQL 기준으로 다시 채운다")
    void 채널_멤버_캐시가_비면_내부_API_로_MySQL_기준으로_다시_채운다() throws Exception {
        // given
        Long teamId = createTeam(alice, uniqueTeamName());
        Long channelId = createChannel(alice, teamId, "cache");
        redisTemplate.delete(ChannelMemberCache.key(channelId));

        // when
        mockMvc.perform(get("/internal/channel/" + channelId + "/members"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$").value(Matchers.contains(alice.intValue())));

        // then
        org.assertj.core.api.Assertions.assertThat(
            redisTemplate.opsForSet().isMember(ChannelMemberCache.key(channelId), String.valueOf(alice))).isTrue();
        redisTemplate.delete(ChannelMemberCache.key(channelId));
    }

    @Test
    @DisplayName("success case : 채널 / DM 목록의 안 읽은 수는 내가 보낸 메시지를 빼고 읽음 처리 이후만 센다")
    void 채널_DM_목록의_안_읽은_수는_내가_보낸_메시지를_빼고_읽음_처리_이후만_센다() throws Exception {
        // given
        Long teamId = createTeam(alice, uniqueTeamName());
        mockMvc.perform(as(bob, post("/v1/private/api/team/" + teamId + "/join"))).andExpect(status().isOk());
        Long channelId = createChannel(alice, teamId, "unread");
        mockMvc.perform(as(bob, post("/v1/private/api/channel/" + channelId + "/join"))).andExpect(status().isOk());
        createChannel(alice, teamId, "notjoined");
        ChatMessage first = saveMessage(channelId, alice, "a1");
        saveMessage(channelId, bob, "bob 이 보낸 것");
        saveMessage(channelId, alice, "a2");
        saveMessage(channelId, alice, "a3");
        Long dmId = openDm(alice, teamId, bob);
        saveMessage(dmId, alice, "dm1");

        // when
        mockMvc.perform(as(bob, put("/v1/private/api/channel/" + channelId + "/read")
                .contentType(MediaType.APPLICATION_JSON).content("{\"messageId\":\"" + first.getId().toHexString() + "\"}")))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.code").value(0))
            .andExpect(jsonPath("$.data").doesNotExist());

        // then
        mockMvc.perform(as(bob, get("/v1/private/api/channel/channels").param("teamId", String.valueOf(teamId))))
            .andExpect(jsonPath("$.data.list[?(@.channelName == 'unread')].unreadCount").value(Matchers.contains(2)))
            .andExpect(jsonPath("$.data.list[?(@.channelName == 'notjoined')].unreadCount").value(Matchers.contains(0)))
            .andExpect(jsonPath("$.data.list[?(@.channelName == 'general')].unreadCount").value(Matchers.contains(0)));
        mockMvc.perform(as(bob, get("/v1/private/api/channel/dms").param("teamId", String.valueOf(teamId))))
            .andExpect(jsonPath("$.data.list[0].channelId").value(dmId.intValue()))
            .andExpect(jsonPath("$.data.list[0].unreadCount").value(1));
        mockMvc.perform(as(alice, get("/v1/private/api/channel/channels").param("teamId", String.valueOf(teamId))))
            .andExpect(jsonPath("$.data.list[?(@.channelName == 'unread')].unreadCount").value(Matchers.contains(1)));
    }

    @Test
    @DisplayName("success case : 읽음 위치는 뒤로 가지 않는다")
    void 읽음_위치는_뒤로_가지_않는다() throws Exception {
        // given
        Long teamId = createTeam(alice, uniqueTeamName());
        Long channelId = createChannel(alice, teamId, "forward");
        ChatMessage older = saveMessage(channelId, bob, "b1");
        ChatMessage newer = saveMessage(channelId, bob, "b2");
        saveMessage(channelId, bob, "b3");
        markRead(alice, channelId, newer.getId().toHexString());

        // when
        markRead(alice, channelId, older.getId().toHexString());

        // then
        mockMvc.perform(as(alice, get("/v1/private/api/channel/" + channelId + "/catch-up")))
            .andExpect(jsonPath("$.data.unreadCount").value(1));
    }

    @Test
    @DisplayName("fail case : 채널 멤버가 아니면 읽음 처리할 수 없고 잘못된 메시지 ID 는 거절한다")
    void 채널_멤버가_아니면_읽음_처리할_수_없고_잘못된_메시지_ID_는_거절한다() throws Exception {
        // given
        Long teamId = createTeam(alice, uniqueTeamName());
        Long channelId = createChannel(alice, teamId, "readfail");
        Long otherChannelId = createChannel(alice, teamId, "readfail2");
        ChatMessage message = saveMessage(channelId, alice, "a1");
        ChatMessage otherChannelMessage = saveMessage(otherChannelId, alice, "다른 채널");

        // when & then
        assertRead(bob, channelId, message.getId().toHexString(), 403, 3003);
        assertRead(alice, channelId, "abc", 400, 4001);
        assertRead(alice, channelId, otherChannelMessage.getId().toHexString(), 400, 4001);
        assertRead(alice, channelId, new ObjectId().toHexString(), 400, 4001);
        assertRead(alice, channelId, "ffffffffffffffffffffffff", 400, 4001);
    }

    @Test
    @DisplayName("success case : 한 번도 읽음 처리를 안 했으면 채널에 들어오기 전 메시지는 안 읽은 것으로 세지 않는다")
    void 한_번도_읽음_처리를_안_했으면_채널에_들어오기_전_메시지는_안_읽은_것으로_세지_않는다() throws Exception {
        // given : 들어오기 1분 전 메시지 3개
        Long teamId = createTeam(alice, uniqueTeamName());
        Long channelId = createChannel(alice, teamId, "joinlater");
        Instant beforeJoin = Instant.now().minusSeconds(60);
        for (int i = 0; i < 3; i++) {
            savedMessages.add(chatMessageRepository.save(ChatMessage.builder().id(new ObjectId(Date.from(beforeJoin)))
                .channelId(channelId).content("old" + i).createUserId(alice).eventPublishDt(beforeJoin).createDt(beforeJoin).build()));
        }
        mockMvc.perform(as(bob, post("/v1/private/api/team/" + teamId + "/join"))).andExpect(status().isOk());
        mockMvc.perform(as(bob, post("/v1/private/api/channel/" + channelId + "/join"))).andExpect(status().isOk());
        // 들어온 시각(channel_user.create_dt)을 DB 에서 다시 읽게 한다 (시간대 변환까지 확인)
        entityManager.flush();
        entityManager.clear();
        ChatMessage firstAfterJoin = saveMessage(channelId, alice, "new1");
        saveMessage(channelId, alice, "new2");

        // when & then
        mockMvc.perform(as(bob, get("/v1/private/api/channel/channels").param("teamId", String.valueOf(teamId))))
            .andExpect(jsonPath("$.data.list[?(@.channelName == 'joinlater')].unreadCount").value(Matchers.contains(2)));
        mockMvc.perform(as(bob, get("/v1/private/api/channel/" + channelId + "/catch-up")))
            .andExpect(jsonPath("$.data.unreadCount").value(2))
            .andExpect(jsonPath("$.data.firstUnreadMessageId").value(firstAfterJoin.getId().toHexString()));
    }

    @Test
    @DisplayName("success case : 채널 목록 안 읽은 수는 채널마다 다른 읽음 위치로 세고 100 에서 자른다")
    void 채널_목록_안_읽은_수는_채널마다_다른_읽음_위치로_세고_100_에서_자른다() throws Exception {
        // given
        Long teamId = createTeam(alice, uniqueTeamName());
        mockMvc.perform(as(bob, post("/v1/private/api/team/" + teamId + "/join"))).andExpect(status().isOk());
        Long many = createChannel(alice, teamId, "many");
        Long partly = createChannel(alice, teamId, "partly");
        joinChannel(bob, many);
        joinChannel(bob, partly);
        for (int i = 0; i < 101; i++) {
            saveMessage(many, alice, "m" + i);
        }
        saveMessage(partly, alice, "p1");
        ChatMessage partlyRead = saveMessage(partly, alice, "p2");
        saveMessage(partly, alice, "p3");
        markRead(bob, partly, partlyRead.getId().toHexString());

        // when & then
        mockMvc.perform(as(bob, get("/v1/private/api/channel/channels").param("teamId", String.valueOf(teamId))))
            .andExpect(jsonPath("$.data.list[?(@.channelName == 'many')].unreadCount").value(Matchers.contains(100)))
            .andExpect(jsonPath("$.data.list[?(@.channelName == 'partly')].unreadCount").value(Matchers.contains(1)))
            .andExpect(jsonPath("$.data.list[?(@.channelName == 'general')].unreadCount").value(Matchers.contains(0)));
        mockMvc.perform(as(bob, get("/v1/private/api/channel/" + many + "/catch-up")))
            .andExpect(jsonPath("$.data.unreadCount").value(101));
    }

    private void joinChannel(Long userId, Long channelId) throws Exception {
        mockMvc.perform(as(userId, post("/v1/private/api/channel/" + channelId + "/join"))).andExpect(status().isOk());
    }

    private void assertRead(Long userId, Long channelId, String messageId, int status, int code) throws Exception {
        mockMvc.perform(as(userId, put("/v1/private/api/channel/" + channelId + "/read")
                .contentType(MediaType.APPLICATION_JSON).content("{\"messageId\":\"" + messageId + "\"}")))
            .andExpect(status().is(status))
            .andExpect(jsonPath("$.code").value(code));
    }

    @Test
    @DisplayName("success case : after 로 마지막으로 받은 메시지 이후를 오래된 순으로 가져오고 before 와 같이 주면 거절한다")
    void after_로_마지막으로_받은_메시지_이후를_오래된_순으로_가져오고_before_와_같이_주면_거절한다() throws Exception {
        // given
        Long teamId = createTeam(alice, uniqueTeamName());
        Long channelId = createChannel(alice, teamId, "after");
        ChatMessage m1 = saveMessage(channelId, alice, "m1");
        saveMessage(channelId, alice, "m2");
        saveMessage(channelId, alice, "m3");
        saveMessage(channelId, alice, "m4");

        // when & then
        mockMvc.perform(as(alice, get("/v1/private/api/channel/" + channelId + "/messages")
                .param("after", m1.getId().toHexString()).param("size", "2")))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.list[*].message").value(Matchers.contains("m2", "m3")))
            .andExpect(jsonPath("$.data.hasNext").value(true));
        mockMvc.perform(as(alice, get("/v1/private/api/channel/" + channelId + "/messages")
                .param("after", m1.getId().toHexString()).param("size", "3")))
            .andExpect(jsonPath("$.data.list[*].message").value(Matchers.contains("m2", "m3", "m4")))
            .andExpect(jsonPath("$.data.hasNext").value(false));
        mockMvc.perform(as(alice, get("/v1/private/api/channel/" + channelId + "/messages")
                .param("after", m1.getId().toHexString()).param("before", m1.getId().toHexString())))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value(4001));
    }

    @Test
    @DisplayName("success case : 따라잡기는 내가 보낸 것을 뺀 안 읽은 수와 첫 안 읽은 메시지를 주고 읽음 처리는 하지 않는다")
    void 따라잡기는_내가_보낸_것을_뺀_안_읽은_수와_첫_안_읽은_메시지를_주고_읽음_처리는_하지_않는다() throws Exception {
        // given
        Long teamId = createTeam(alice, uniqueTeamName());
        Long channelId = createChannel(alice, teamId, "catchup");
        mockMvc.perform(as(bob, post("/v1/private/api/team/" + teamId + "/join"))).andExpect(status().isOk());
        mockMvc.perform(as(bob, post("/v1/private/api/channel/" + channelId + "/join"))).andExpect(status().isOk());
        ChatMessage read = saveMessage(channelId, alice, "읽은 것");
        markRead(bob, channelId, read.getId().toHexString());
        saveMessage(channelId, bob, "내가 보낸 것");
        ChatMessage firstUnread = saveMessage(channelId, alice, "u1");
        saveMessage(channelId, alice, "u2");

        // when
        mockMvc.perform(as(bob, get("/v1/private/api/channel/" + channelId + "/catch-up")))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.unreadCount").value(2))
            .andExpect(jsonPath("$.data.firstUnreadMessageId").value(firstUnread.getId().toHexString()))
            .andExpect(jsonPath("$.data.summary").doesNotExist());

        // then
        mockMvc.perform(as(bob, get("/v1/private/api/channel/" + channelId + "/catch-up")))
            .andExpect(jsonPath("$.data.unreadCount").value(2));
    }

    @Test
    @DisplayName("success case : 안 읽은 메시지가 없으면 0 과 null 이다")
    void 안_읽은_메시지가_없으면_0_과_null_이다() throws Exception {
        // given
        Long teamId = createTeam(alice, uniqueTeamName());
        Long channelId = createChannel(alice, teamId, "allread");
        saveMessage(channelId, alice, "내가 보낸 것");

        // when & then
        mockMvc.perform(as(alice, get("/v1/private/api/channel/" + channelId + "/catch-up")))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.unreadCount").value(0))
            .andExpect(jsonPath("$.data.firstUnreadMessageId").value(Matchers.nullValue()));
    }

    @Test
    @DisplayName("fail case : 채널 멤버가 아니면 따라잡기를 볼 수 없다")
    void 채널_멤버가_아니면_따라잡기를_볼_수_없다() throws Exception {
        // given
        Long teamId = createTeam(alice, uniqueTeamName());
        Long channelId = createChannel(alice, teamId, "nocatchup");

        // when & then
        mockMvc.perform(as(bob, get("/v1/private/api/channel/" + channelId + "/catch-up")))
            .andExpect(status().isForbidden())
            .andExpect(jsonPath("$.code").value(3003));
    }

    @Test
    @DisplayName("success case : 팀 멤버 목록에 지금 채팅 서버에 연결된 사람만 online 으로 표시한다")
    void 팀_멤버_목록에_지금_채팅_서버에_연결된_사람만_online_으로_표시한다() throws Exception {
        // given
        Long teamId = createTeam(alice, uniqueTeamName());
        mockMvc.perform(as(bob, post("/v1/private/api/team/" + teamId + "/join"))).andExpect(status().isOk());
        mockMvc.perform(as(carol, post("/v1/private/api/team/" + teamId + "/join"))).andExpect(status().isOk());
        String chatServer = "10.255.255.1:" + (40000 + (int) (Math.random() * 20000));
        String chatServerKey = "chatServerUri:" + chatServer;
        redisKeys.addAll(List.of(String.valueOf(bob), String.valueOf(carol), chatServerKey));
        // 채팅 서버가 쓰는 모양 그대로 : bob 은 연결 중, carol 은 끊김(라우팅 키만 남음), alice 는 키 없음
        redisTemplate.opsForValue().set(String.valueOf(bob), "ws://" + chatServer);
        redisTemplate.opsForValue().set(String.valueOf(carol), "ws://" + chatServer);
        redisTemplate.opsForSet().add(chatServerKey, String.valueOf(bob));

        // when & then
        mockMvc.perform(as(alice, get("/v1/private/api/team/" + teamId + "/members")))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.list[?(@.userId == " + alice + ")].online").value(Matchers.contains(false)))
            .andExpect(jsonPath("$.data.list[?(@.userId == " + bob + ")].online").value(Matchers.contains(true)))
            .andExpect(jsonPath("$.data.list[?(@.userId == " + carol + ")].online").value(Matchers.contains(false)));
    }

    private ChatMessage saveMessage(Long channelId, Long sendUserId, String content) {
        ChatMessage message = chatMessageRepository.save(ChatMessage.builder().channelId(channelId).content(content)
            .createUserId(sendUserId).eventPublishDt(Instant.now()).createDt(Instant.now()).build());
        savedMessages.add(message);
        return message;
    }

    private void markRead(Long userId, Long channelId, String messageId) throws Exception {
        mockMvc.perform(as(userId, put("/v1/private/api/channel/" + channelId + "/read")
                .contentType(MediaType.APPLICATION_JSON).content("{\"messageId\":\"" + messageId + "\"}")))
            .andExpect(status().isOk());
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
