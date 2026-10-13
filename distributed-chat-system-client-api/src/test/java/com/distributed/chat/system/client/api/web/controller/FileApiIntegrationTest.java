package com.distributed.chat.system.client.api.web.controller;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.distributed.chat.system.mongodb.collection.ChatMessage;
import com.distributed.chat.system.mongodb.repository.ChatMessageRepository;
import com.distributed.chat.system.mysql.entity.User;
import com.distributed.chat.system.mysql.repository.UserRepository;
import com.jayway.jsonpath.JsonPath;
import jakarta.transaction.Transactional;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.bson.types.ObjectId;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.gridfs.GridFsTemplate;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

@DisplayName("[통합] 첨부파일 API 테스트")
@SpringBootTest(properties = "eureka.client.enabled=false")
@AutoConfigureMockMvc
@Transactional
class FileApiIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private GridFsTemplate gridFsTemplate;

    @Autowired
    private ChatMessageRepository chatMessageRepository;

    private Long alice;
    private Long bob;
    private Long channelId;

    /**
     * GridFS / 메시지는 트랜잭션 롤백이 안 되므로 직접 지운다
     */
    private final List<String> fileIds = new ArrayList<>();
    private final List<ChatMessage> messages = new ArrayList<>();

    @BeforeEach
    void setup() throws Exception {
        alice = saveUser("alice");
        bob = saveUser("bob");
        String body = mockMvc.perform(as(alice, post("/v1/private/api/team/team")
                .contentType(MediaType.APPLICATION_JSON).content("{\"teamName\":\"team_" + UUID.randomUUID().toString().substring(0, 8) + "\"}")))
            .andExpect(status().isOk())
            .andReturn().getResponse().getContentAsString();
        channelId = ((Number) JsonPath.read(body, "$.data.defaultChannelId")).longValue();
    }

    @AfterEach
    void tearDown() {
        fileIds.forEach(id -> gridFsTemplate.delete(Query.query(Criteria.where("_id").is(new ObjectId(id)))));
        chatMessageRepository.deleteAll(messages);
    }

    @Test
    @DisplayName("success case : 올린 이미지를 같은 채널 멤버가 inline 으로 그대로 내려받는다")
    void 올린_이미지를_같은_채널_멤버가_inline_으로_그대로_내려받는다() throws Exception {
        // given
        byte[] image = {(byte) 0x89, 'P', 'N', 'G', 1, 2, 3};
        String fileId = upload(alice, new MockMultipartFile("file", "사진.png", "image/png", image));

        // when & then
        mockMvc.perform(as(alice, get("/v1/private/api/file/" + fileId)))
            .andExpect(status().isOk())
            .andExpect(content().bytes(image))
            .andExpect(header().string("Content-Type", "image/png"))
            .andExpect(header().string("Content-Length", String.valueOf(image.length)))
            .andExpect(header().string("Content-Disposition", "inline; filename*=UTF-8''%EC%82%AC%EC%A7%84.png"))
            .andExpect(header().string("Cache-Control", "private, max-age=31536000, immutable"))
            .andExpect(header().string("X-Content-Type-Options", "nosniff"));
    }

    @Test
    @DisplayName("success case : 업로드 응답은 fileId, name, contentType, size 이고 이미지가 아니면 attachment 로 내려준다")
    void 업로드_응답은_fileId_name_contentType_size_이고_이미지가_아니면_attachment_로_내려준다() throws Exception {
        // given
        byte[] pdf = "%PDF-1.4".getBytes(StandardCharsets.UTF_8);

        // when
        String body = mockMvc.perform(as(alice, multipart("/v1/private/api/file")
                .file(new MockMultipartFile("file", "회의록.pdf", "application/pdf", pdf))
                .param("channelId", String.valueOf(channelId))))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.name").value("회의록.pdf"))
            .andExpect(jsonPath("$.data.contentType").value("application/pdf"))
            .andExpect(jsonPath("$.data.size").value(pdf.length))
            .andReturn().getResponse().getContentAsString();
        String fileId = JsonPath.read(body, "$.data.fileId");
        fileIds.add(fileId);

        // then
        mockMvc.perform(as(alice, get("/v1/private/api/file/" + fileId)))
            .andExpect(status().isOk())
            .andExpect(header().string("Content-Disposition", "attachment; filename*=UTF-8''%ED%9A%8C%EC%9D%98%EB%A1%9D.pdf"));
    }

    @Test
    @DisplayName("fail case : 채널 멤버가 아니면 올릴 수도 내려받을 수도 없다")
    void 채널_멤버가_아니면_올릴_수도_내려받을_수도_없다() throws Exception {
        // given
        String fileId = upload(alice, new MockMultipartFile("file", "a.txt", "text/plain", new byte[]{1}));

        // when & then
        mockMvc.perform(as(bob, multipart("/v1/private/api/file")
                .file(new MockMultipartFile("file", "b.txt", "text/plain", new byte[]{1}))
                .param("channelId", String.valueOf(channelId))))
            .andExpect(status().isForbidden())
            .andExpect(jsonPath("$.code").value(3003));
        mockMvc.perform(as(bob, get("/v1/private/api/file/" + fileId)))
            .andExpect(status().isForbidden())
            .andExpect(jsonPath("$.code").value(3003));
    }

    @Test
    @DisplayName("fail case : 빈 파일은 4004, 없는 파일과 틀린 ID 는 4005 다")
    void 빈_파일은_4004_없는_파일과_틀린_ID_는_4005_다() throws Exception {
        // when & then
        mockMvc.perform(as(alice, multipart("/v1/private/api/file")
                .file(new MockMultipartFile("file", "empty.txt", "text/plain", new byte[0]))
                .param("channelId", String.valueOf(channelId))))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value(4004));
        mockMvc.perform(as(alice, get("/v1/private/api/file/" + new ObjectId().toHexString())))
            .andExpect(status().isNotFound())
            .andExpect(jsonPath("$.code").value(4005));
        mockMvc.perform(as(alice, get("/v1/private/api/file/abc")))
            .andExpect(status().isNotFound())
            .andExpect(jsonPath("$.code").value(4005));
    }

    @Test
    @DisplayName("success case : 이전 메시지 조회 응답에 첨부와 리액션이 들어가고 없으면 빈 배열이다")
    void 이전_메시지_조회_응답에_첨부와_리액션이_들어가고_없으면_빈_배열이다() throws Exception {
        // given
        messages.add(chatMessageRepository.save(ChatMessage.builder().channelId(channelId).content("").createUserId(alice)
            .eventPublishDt(Instant.now()).createDt(Instant.now())
            .attachments(List.of(new ChatMessage.Attachment("6acc66675f99715e286f0f6e", "사진.png", "image/png", 10L)))
            .reactions(List.of(new ChatMessage.Reaction("👍", List.of(alice, bob))))
            .build()));
        messages.add(chatMessageRepository.save(ChatMessage.builder().channelId(channelId).content("그냥 글").createUserId(alice)
            .eventPublishDt(Instant.now()).createDt(Instant.now()).build()));

        // when & then
        mockMvc.perform(as(alice, get("/v1/private/api/channel/" + channelId + "/messages")))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.list[0].attachments[0].fileId").value("6acc66675f99715e286f0f6e"))
            .andExpect(jsonPath("$.data.list[0].attachments[0].name").value("사진.png"))
            .andExpect(jsonPath("$.data.list[0].attachments[0].contentType").value("image/png"))
            .andExpect(jsonPath("$.data.list[0].attachments[0].size").value(10))
            .andExpect(jsonPath("$.data.list[0].reactions[0].emoji").value("👍"))
            .andExpect(jsonPath("$.data.list[0].reactions[0].userIds.length()").value(2))
            .andExpect(jsonPath("$.data.list[1].attachments.length()").value(0))
            .andExpect(jsonPath("$.data.list[1].reactions.length()").value(0));
    }

    private String upload(Long userId, MockMultipartFile file) throws Exception {
        String body = mockMvc.perform(as(userId, multipart("/v1/private/api/file").file(file)
                .param("channelId", String.valueOf(channelId))))
            .andExpect(status().isOk())
            .andReturn().getResponse().getContentAsString();
        String fileId = JsonPath.read(body, "$.data.fileId");
        fileIds.add(fileId);
        return fileId;
    }

    private MockHttpServletRequestBuilder as(Long userId, MockHttpServletRequestBuilder request) {
        return request.with(user(String.valueOf(userId)));
    }

    private Long saveUser(String name) {
        String account = name + "_" + UUID.randomUUID().toString().substring(0, 8);
        return userRepository.save(User.builder().userName(account).account(account).password("hash").build()).getId();
    }
}
