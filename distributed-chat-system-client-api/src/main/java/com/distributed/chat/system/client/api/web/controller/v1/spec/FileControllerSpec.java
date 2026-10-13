package com.distributed.chat.system.client.api.web.controller.v1.spec;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.core.io.Resource;
import org.springframework.http.ResponseEntity;
import org.springframework.web.multipart.MultipartFile;

@Tag(name = "첨부파일 API", description = "채팅 첨부파일 업로드 / 내려받기 (MongoDB GridFS)")
public interface FileControllerSpec {

    @Operation(summary = "첨부파일 업로드", description = "multipart file + channelId. 채널 멤버만, 최대 20MB. 받은 fileId 를 메시지 attachments 에 넣어 보낸다 (3003 채널 멤버 아님, 4003 너무 큼, 4004 빈 파일)")
    ResponseEntity upload(@Parameter(description = "파일") MultipartFile file, @Parameter(description = "채널 ID") Long channelId);

    @Operation(summary = "첨부파일 내려받기", description = "파일을 올린 채널의 멤버만. 이미지는 inline, 그 외는 attachment (3003 채널 멤버 아님, 4005 없는 파일)")
    ResponseEntity<Resource> download(@Parameter(description = "파일 ID") String fileId);
}
