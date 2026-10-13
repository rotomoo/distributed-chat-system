package com.distributed.chat.system.mongodb.file;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.bson.types.ObjectId;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.gridfs.GridFsResource;
import org.springframework.data.mongodb.gridfs.GridFsTemplate;

@DisplayName("[통합] ChatFileStore (GridFS) 테스트")
@SpringBootTest
class ChatFileStoreIntegrationTest {

    @Autowired
    private ChatFileStore chatFileStore;

    @Autowired
    private GridFsTemplate gridFsTemplate;

    private final List<String> storedFileIds = new ArrayList<>();

    @AfterEach
    void tearDown() {
        storedFileIds.forEach(id -> gridFsTemplate.delete(Query.query(Criteria.where("_id").is(new ObjectId(id)))));
    }

    @Test
    @DisplayName("success case : 저장한 파일의 metadata 와 본문을 그대로 읽는다")
    void 저장한_파일의_metadata_와_본문을_그대로_읽는다() throws Exception {
        // given
        byte[] body = "안녕하세요 첨부파일".getBytes(StandardCharsets.UTF_8);
        ChatFile stored = store(body, 7L, 5L, "text/plain", "회의록.txt");

        // when
        Optional<ChatFileDownload> download = chatFileStore.findForDownload(stored.fileId());

        // then
        assertThat(download).isPresent();
        ChatFile file = download.get().file();
        assertThat(file).isEqualTo(new ChatFile(stored.fileId(), 7L, 5L, "text/plain", "회의록.txt", body.length));
        GridFsResource resource = download.get().open();
        try (InputStream content = resource.getInputStream()) {
            assertThat(content.readAllBytes()).isEqualTo(body);
        }
    }

    @Test
    @DisplayName("success case : chunk 크기(255KB)보다 큰 파일도 그대로 읽는다")
    void chunk_크기보다_큰_파일도_그대로_읽는다() throws Exception {
        // given
        byte[] body = new byte[600 * 1024];
        for (int i = 0; i < body.length; i++) {
            body[i] = (byte) (i % 251);
        }
        ChatFile stored = store(body, 7L, 5L, "application/octet-stream", "big.bin");

        // when
        GridFsResource resource = chatFileStore.findForDownload(stored.fileId()).orElseThrow().open();

        // then
        assertThat(resource.contentLength()).isEqualTo(body.length);
        try (InputStream content = resource.getInputStream()) {
            assertThat(content.readAllBytes()).isEqualTo(body);
        }
    }

    @Test
    @DisplayName("fail case : 없는 파일이나 형식이 틀린 ID 는 찾지 못한다")
    void 없는_파일이나_형식이_틀린_ID_는_찾지_못한다() {
        // when & then
        assertThat(chatFileStore.findForDownload(new ObjectId().toHexString())).isEmpty();
        assertThat(chatFileStore.findForDownload("abc")).isEmpty();
        assertThat(chatFileStore.findForDownload(null)).isEmpty();
    }

    @Test
    @DisplayName("success case : 여러 파일의 metadata 를 한 번에 찾고 없는 ID 와 틀린 ID 는 빠진다")
    void 여러_파일의_metadata_를_한_번에_찾고_없는_ID_와_틀린_ID_는_빠진다() {
        // given
        ChatFile first = store(new byte[]{1}, 7L, 5L, "image/png", "a.png");
        ChatFile second = store(new byte[]{2, 3}, 8L, 6L, "image/jpeg", "b.jpg");

        // when
        List<ChatFile> found = chatFileStore.findAllById(List.of(first.fileId(), second.fileId(), new ObjectId().toHexString(), "abc"));

        // then
        assertThat(found).containsExactlyInAnyOrder(first, second);
        assertThat(chatFileStore.findAllById(List.of("abc"))).isEmpty();
    }

    private ChatFile store(byte[] body, Long channelId, Long uploaderId, String contentType, String name) {
        ChatFile stored = chatFileStore.store(new ByteArrayInputStream(body), channelId, uploaderId, contentType, name, body.length);
        storedFileIds.add(stored.fileId());
        return stored;
    }
}
