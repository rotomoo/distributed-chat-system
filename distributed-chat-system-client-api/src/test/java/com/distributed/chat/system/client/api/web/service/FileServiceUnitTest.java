package com.distributed.chat.system.client.api.web.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowableOfType;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.distributed.chat.system.client.api.web.dto.file.FileDto;
import com.distributed.chat.system.common.exception.ApiException;
import com.distributed.chat.system.common.exception.ErrorCode;
import com.distributed.chat.system.common.response.ResponseData;
import com.distributed.chat.system.mongodb.file.ChatFile;
import com.distributed.chat.system.mongodb.file.ChatFileDownload;
import com.distributed.chat.system.mongodb.file.ChatFileStore;
import com.distributed.chat.system.mysql.repository.ChannelUserRepository;
import java.io.InputStream;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.core.io.Resource;
import org.springframework.data.mongodb.gridfs.GridFsResource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.multipart.MultipartFile;

@DisplayName("[단위] FileService 테스트")
@ExtendWith(MockitoExtension.class)
class FileServiceUnitTest {

    private static final Long LOGIN_ID = 5L;
    private static final Long CHANNEL_ID = 7L;
    private static final String FILE_ID = "6acc66675f99715e286f0f6e";

    @Mock
    private ChatFileStore chatFileStore;

    @Mock
    private ChannelUserRepository channelUserRepository;

    private FileService fileService;

    @BeforeEach
    void setup() {
        fileService = new FileService(chatFileStore, new ChannelMemberGuard(channelUserRepository));
        SecurityContextHolder.getContext().setAuthentication(
            new UsernamePasswordAuthenticationToken(String.valueOf(LOGIN_ID), null, List.of()));
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    // ── 업로드 ──────────────────────────────────────────────────

    @Test
    @DisplayName("success case : 채널 멤버가 올린 파일을 채널, 올린 사람, 형식, 이름, 크기와 함께 저장한다")
    void 채널_멤버가_올린_파일을_채널_올린_사람_형식_이름_크기와_함께_저장한다() {
        // arrange
        givenChannelMember();
        MockMultipartFile file = new MockMultipartFile("file", "회의록.pdf", "application/pdf", new byte[]{1, 2, 3});
        when(chatFileStore.store(any(InputStream.class), eq(CHANNEL_ID), eq(LOGIN_ID), eq("application/pdf"), eq("회의록.pdf"), eq(3L)))
            .thenReturn(new ChatFile(FILE_ID, CHANNEL_ID, LOGIN_ID, "application/pdf", "회의록.pdf", 3L));

        // act
        ResponseData<FileDto> response = fileService.upload(CHANNEL_ID, file);

        // assert
        FileDto uploaded = response.getData();
        assertThat(uploaded.getFileId()).isEqualTo(FILE_ID);
        assertThat(uploaded.getName()).isEqualTo("회의록.pdf");
        assertThat(uploaded.getContentType()).isEqualTo("application/pdf");
        assertThat(uploaded.getSize()).isEqualTo(3L);
    }

    @Test
    @DisplayName("success case : 형식이 없거나 틀리면 octet-stream 으로, 경로가 붙은 이름은 마지막 이름만 저장한다")
    void 형식이_없거나_틀리면_octet_stream_으로_경로가_붙은_이름은_마지막_이름만_저장한다() {
        // arrange
        givenChannelMember();
        MockMultipartFile file = new MockMultipartFile("file", "C:\\Users\\me\\사진.png", "잘못된 형식", new byte[]{1});
        when(chatFileStore.store(any(InputStream.class), eq(CHANNEL_ID), eq(LOGIN_ID), eq("application/octet-stream"), eq("사진.png"), eq(1L)))
            .thenReturn(new ChatFile(FILE_ID, CHANNEL_ID, LOGIN_ID, "application/octet-stream", "사진.png", 1L));

        // act
        ResponseData<FileDto> response = fileService.upload(CHANNEL_ID, file);

        // assert
        assertThat(response.getData().getName()).isEqualTo("사진.png");
    }

    @Test
    @DisplayName("fail case : 채널 멤버가 아니면 올릴 수 없다")
    void 채널_멤버가_아니면_올릴_수_없다() {
        // arrange
        when(channelUserRepository.existsByChannelIdAndUserIdAndDeleteYnIsFalse(CHANNEL_ID, LOGIN_ID)).thenReturn(false);

        // act
        ApiException exception = catchThrowableOfType(() -> fileService.upload(CHANNEL_ID,
            new MockMultipartFile("file", "a.txt", "text/plain", new byte[]{1})), ApiException.class);

        // assert
        assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.NOT_CHANNEL_MEMBER);
        verifyNoInteractions(chatFileStore);
    }

    @Test
    @DisplayName("fail case : 빈 파일은 올릴 수 없다")
    void 빈_파일은_올릴_수_없다() {
        // arrange
        givenChannelMember();

        // act
        ApiException exception = catchThrowableOfType(() -> fileService.upload(CHANNEL_ID,
            new MockMultipartFile("file", "a.txt", "text/plain", new byte[0])), ApiException.class);

        // assert
        assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.EMPTY_FILE);
        verifyNoInteractions(chatFileStore);
    }

    @Test
    @DisplayName("fail case : 20MB 를 넘는 파일은 올릴 수 없고 딱 20MB 는 올릴 수 있다")
    void _20MB_를_넘는_파일은_올릴_수_없고_딱_20MB_는_올릴_수_있다() throws Exception {
        // arrange
        givenChannelMember();
        MultipartFile tooLarge = sizedFile(20L * 1024 * 1024 + 1);
        MultipartFile max = sizedFile(20L * 1024 * 1024);
        when(chatFileStore.store(any(), anyLong(), anyLong(), any(), any(), eq(20L * 1024 * 1024)))
            .thenReturn(new ChatFile(FILE_ID, CHANNEL_ID, LOGIN_ID, "application/octet-stream", "big.bin", 20L * 1024 * 1024));

        // act
        ApiException exception = catchThrowableOfType(() -> fileService.upload(CHANNEL_ID, tooLarge), ApiException.class);
        ResponseData<FileDto> uploaded = fileService.upload(CHANNEL_ID, max);

        // assert
        assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.FILE_TOO_LARGE);
        assertThat(uploaded.getData().getSize()).isEqualTo(20L * 1024 * 1024);
    }

    @Test
    @DisplayName("success case : 파일 이름은 제어 문자를 지우고 255자로 자르고 비면 file 이다")
    void 파일_이름은_제어_문자를_지우고_255자로_자르고_비면_file_이다() {
        // act & assert
        assertThat(FileService.fileName("a\nb\u0000.txt")).isEqualTo("ab.txt");
        assertThat(FileService.fileName("가".repeat(300))).hasSize(255);
        assertThat(FileService.fileName("/tmp/  ")).isEqualTo("file");
        assertThat(FileService.fileName(null)).isEqualTo("file");
        assertThat(FileService.contentType("IMAGE/PNG; name=a")).isEqualTo("image/png");
        assertThat(FileService.contentType(null)).isEqualTo("application/octet-stream");
    }

    // ── 내려받기 ────────────────────────────────────────────────

    @Test
    @DisplayName("success case : 이미지는 inline 으로 형식, 크기, 캐시 헤더와 함께 본문을 흘려보낸다")
    void 이미지는_inline_으로_형식_크기_캐시_헤더와_함께_본문을_흘려보낸다() {
        // arrange
        givenChannelMember();
        GridFsResource content = mock(GridFsResource.class);
        givenFile(new ChatFile(FILE_ID, CHANNEL_ID, 9L, "image/png", "사진.png", 1234L), content);

        // act
        ResponseEntity<Resource> response = fileService.download(FILE_ID);

        // assert
        assertThat(response.getBody()).isSameAs(content);
        HttpHeaders headers = response.getHeaders();
        assertThat(headers.getContentType()).hasToString("image/png");
        assertThat(headers.getContentLength()).isEqualTo(1234L);
        assertThat(headers.getFirst(HttpHeaders.CONTENT_DISPOSITION))
            .isEqualTo("inline; filename*=UTF-8''%EC%82%AC%EC%A7%84.png");
        assertThat(headers.getFirst(HttpHeaders.CACHE_CONTROL)).isEqualTo("private, max-age=31536000, immutable");
    }

    @Test
    @DisplayName("success case : 이미지가 아니거나 SVG 면 attachment 로 내려준다")
    void 이미지가_아니거나_SVG_면_attachment_로_내려준다() {
        // act
        String pdf = FileService.contentDisposition(new ChatFile(FILE_ID, CHANNEL_ID, 9L, "application/pdf", "회의록.pdf", 1L));
        String svg = FileService.contentDisposition(new ChatFile(FILE_ID, CHANNEL_ID, 9L, "image/svg+xml", "logo.svg", 1L));
        String spaced = FileService.contentDisposition(new ChatFile(FILE_ID, CHANNEL_ID, 9L, "text/plain", "a b;\"c\".txt", 1L));

        // assert
        assertThat(pdf).isEqualTo("attachment; filename*=UTF-8''%ED%9A%8C%EC%9D%98%EB%A1%9D.pdf");
        assertThat(svg).isEqualTo("attachment; filename*=UTF-8''logo.svg");
        assertThat(spaced).isEqualTo("attachment; filename*=UTF-8''a%20b%3B%22c%22.txt");
    }

    @Test
    @DisplayName("fail case : 없는 파일은 4005 다")
    void 없는_파일은_4005_다() {
        // arrange
        when(chatFileStore.findForDownload(FILE_ID)).thenReturn(Optional.empty());

        // act
        ApiException exception = catchThrowableOfType(() -> fileService.download(FILE_ID), ApiException.class);

        // assert
        assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.NOT_EXIST_FILE);
        assertThat(exception.getErrorCode().getHttpStatus().value()).isEqualTo(404);
    }

    @Test
    @DisplayName("fail case : 파일을 올린 채널의 멤버가 아니면 본문을 열지 않고 거절한다")
    void 파일을_올린_채널의_멤버가_아니면_본문을_열지_않고_거절한다() {
        // arrange
        AtomicInteger opened = new AtomicInteger();
        when(chatFileStore.findForDownload(FILE_ID)).thenReturn(Optional.of(new ChatFileDownload(
            new ChatFile(FILE_ID, CHANNEL_ID, 9L, "image/png", "a.png", 1L), () -> {
                opened.incrementAndGet();
                return mock(GridFsResource.class);
            })));
        when(channelUserRepository.existsByChannelIdAndUserIdAndDeleteYnIsFalse(CHANNEL_ID, LOGIN_ID)).thenReturn(false);

        // act
        ApiException exception = catchThrowableOfType(() -> fileService.download(FILE_ID), ApiException.class);

        // assert
        assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.NOT_CHANNEL_MEMBER);
        assertThat(opened).hasValue(0);
    }

    private void givenChannelMember() {
        when(channelUserRepository.existsByChannelIdAndUserIdAndDeleteYnIsFalse(CHANNEL_ID, LOGIN_ID)).thenReturn(true);
    }

    private void givenFile(ChatFile file, GridFsResource content) {
        when(chatFileStore.findForDownload(FILE_ID)).thenReturn(Optional.of(new ChatFileDownload(file, () -> content)));
    }

    private MultipartFile sizedFile(long size) throws Exception {
        MultipartFile file = mock(MultipartFile.class);
        when(file.isEmpty()).thenReturn(false);
        when(file.getSize()).thenReturn(size);
        org.mockito.Mockito.lenient().when(file.getOriginalFilename()).thenReturn("big.bin");
        org.mockito.Mockito.lenient().when(file.getInputStream()).thenReturn(InputStream.nullInputStream());
        return file;
    }
}
