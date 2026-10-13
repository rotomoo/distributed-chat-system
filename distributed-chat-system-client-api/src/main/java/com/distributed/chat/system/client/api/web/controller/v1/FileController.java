package com.distributed.chat.system.client.api.web.controller.v1;

import com.distributed.chat.system.client.api.web.controller.v1.spec.FileControllerSpec;
import com.distributed.chat.system.client.api.web.service.FileService;
import com.distributed.chat.system.common.response.ResponseData;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.Resource;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/v1/private/api/file")
@RequiredArgsConstructor
@Slf4j
public class FileController implements FileControllerSpec {

    private final FileService fileService;

    /**
     * 첨부파일 업로드 (채널 멤버만)
     *
     * @param file
     * @param channelId
     * @return
     */
    @Override
    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<?> upload(@RequestPart("file") MultipartFile file, @RequestParam Long channelId) {
        ResponseData responseData = fileService.upload(channelId, file);

        return ResponseEntity.ok(responseData);
    }

    /**
     * 첨부파일 내려받기 (파일을 올린 채널의 멤버만). 본문은 스트리밍
     *
     * @param fileId
     * @return
     */
    @Override
    @GetMapping("/{fileId}")
    public ResponseEntity<Resource> download(@PathVariable String fileId) {
        return fileService.download(fileId);
    }
}
