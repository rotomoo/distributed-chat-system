package com.distributed.chat.system.client.api.web.service;

import com.distributed.chat.system.client.api.base.security.SecurityUtil;
import com.distributed.chat.system.client.api.web.dto.file.FileDto;
import com.distributed.chat.system.common.exception.ApiException;
import com.distributed.chat.system.common.exception.ErrorCode;
import com.distributed.chat.system.common.response.ResponseData;
import com.distributed.chat.system.mongodb.file.ChatFile;
import com.distributed.chat.system.mongodb.file.ChatFileDownload;
import com.distributed.chat.system.mongodb.file.ChatFileStore;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.InvalidMediaTypeException;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.util.UriUtils;

/**
 * 채팅 첨부파일 (MongoDB GridFS). 올린 채널의 멤버만 올리고 내려받는다
 */
@Service
@RequiredArgsConstructor
public class FileService {

    /**
     * 파일 하나 최대 크기. spring.servlet.multipart.max-file-size 와 같은 값 (넘으면 컨트롤러 전에 4003)
     */
    public static final long MAX_FILE_SIZE = 20L * 1024 * 1024;

    static final int MAX_NAME_LENGTH = 255;
    static final String DEFAULT_NAME = "file";

    /**
     * 스크립트를 담을 수 있는 이미지 형식 (inline 으로 열지 않는다)
     */
    static final String SVG = "image/svg+xml";

    /**
     * 파일은 바뀌지 않으므로 브라우저가 오래 캐시해도 된다 (로그인한 사람 것이므로 private)
     */
    static final String CACHE_CONTROL = "private, max-age=31536000, immutable";

    private final ChatFileStore chatFileStore;
    private final ChannelMemberGuard channelMemberGuard;

    /**
     * 업로드. 본문은 GridFS 로 흘려 넣는다 (메모리에 전체를 올리지 않음)
     */
    public ResponseData upload(Long channelId, MultipartFile file) {
        Long loginId = SecurityUtil.getLoginId();
        channelMemberGuard.requireMember(channelId, loginId);
        if (file == null || file.isEmpty()) {
            throw new ApiException(ErrorCode.EMPTY_FILE);
        }
        if (file.getSize() > MAX_FILE_SIZE) {
            throw new ApiException(ErrorCode.FILE_TOO_LARGE);
        }

        ChatFile stored;
        try (InputStream content = file.getInputStream()) {
            stored = chatFileStore.store(content, channelId, loginId, contentType(file.getContentType()),
                fileName(file.getOriginalFilename()), file.getSize());
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }

        return ResponseData.success("파일 업로드", new FileDto(stored.fileId(), stored.originalName(), stored.contentType(), stored.size()));
    }

    /**
     * 내려받기. 권한을 확인한 뒤에 본문을 열고, 응답으로 흘려보낸다
     */
    public ResponseEntity<Resource> download(String fileId) {
        Long loginId = SecurityUtil.getLoginId();
        ChatFileDownload download = chatFileStore.findForDownload(fileId)
            .orElseThrow(() -> new ApiException(ErrorCode.NOT_EXIST_FILE));
        ChatFile file = download.file();
        channelMemberGuard.requireMember(file.channelId(), loginId);

        return ResponseEntity.ok()
            .contentType(MediaType.parseMediaType(contentType(file.contentType())))
            .contentLength(file.size())
            .header(HttpHeaders.CONTENT_DISPOSITION, contentDisposition(file))
            .header(HttpHeaders.CACHE_CONTROL, CACHE_CONTROL)
            .body(download.open());
    }

    /**
     * 이미지는 화면에 바로 보이게 inline, 그 외는 저장(attachment). 이름은 filename*=UTF-8''... (RFC 5987, 한글 이름)
     * <p>
     * SVG 는 스크립트를 담을 수 있어 같은 도메인에서 열리면 위험하므로 이미지여도 attachment
     */
    static String contentDisposition(ChatFile file) {
        String type = contentType(file.contentType());
        boolean inline = type.startsWith("image/") && !type.equals(SVG);
        return (inline ? "inline" : "attachment") + "; filename*=UTF-8''" + UriUtils.encode(fileName(file.originalName()), StandardCharsets.UTF_8);
    }

    /**
     * 클라이언트가 보낸 형식이 없거나 틀리면 application/octet-stream
     */
    static String contentType(String contentType) {
        if (contentType == null || contentType.isBlank()) {
            return MediaType.APPLICATION_OCTET_STREAM_VALUE;
        }
        try {
            MediaType mediaType = MediaType.parseMediaType(contentType);
            return (mediaType.getType() + "/" + mediaType.getSubtype()).toLowerCase();
        } catch (InvalidMediaTypeException e) {
            return MediaType.APPLICATION_OCTET_STREAM_VALUE;
        }
    }

    /**
     * 경로가 붙어 오는 브라우저가 있어 마지막 이름만 쓰고, 제어 문자는 지우고, 255자로 자른다
     */
    static String fileName(String originalName) {
        if (originalName == null) {
            return DEFAULT_NAME;
        }
        String name = originalName.substring(Math.max(originalName.lastIndexOf('/'), originalName.lastIndexOf('\\')) + 1)
            .replaceAll("\\p{Cntrl}", "")
            .trim();
        if (name.isEmpty()) {
            return DEFAULT_NAME;
        }
        return name.length() > MAX_NAME_LENGTH ? name.substring(0, MAX_NAME_LENGTH) : name;
    }
}
