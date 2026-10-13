package com.distributed.chat.system.client.api.web.dto.file;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 업로드 결과이자 메시지 첨부 항목 ({fileId, name, contentType, size})
 */
@Getter
@NoArgsConstructor
@AllArgsConstructor
@Schema(name = "첨부파일")
public class FileDto {

    @Schema(name = "fileId", description = "파일 ID (GridFS ObjectId)", example = "6acc66675f99715e286f0f6e")
    private String fileId;

    @Schema(name = "name", description = "원래 파일 이름", example = "회의록.pdf")
    private String name;

    @Schema(name = "contentType", description = "파일 형식", example = "application/pdf")
    private String contentType;

    @Schema(name = "size", description = "크기 (byte)", example = "102400")
    private Long size;
}
