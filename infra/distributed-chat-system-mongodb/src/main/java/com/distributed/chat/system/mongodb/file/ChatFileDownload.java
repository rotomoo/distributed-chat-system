package com.distributed.chat.system.mongodb.file;

import java.util.function.Supplier;
import org.springframework.data.mongodb.gridfs.GridFsResource;

/**
 * @param file    metadata (권한 확인용)
 * @param content 본문. 부를 때 GridFS 다운로드 스트림을 연다 (chunk 단위로 읽으므로 파일 전체를 메모리에 올리지 않는다)
 */
public record ChatFileDownload(ChatFile file, Supplier<GridFsResource> content) {

    public GridFsResource open() {
        return content.get();
    }
}
