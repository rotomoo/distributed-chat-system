package com.distributed.chat.system.mongodb.file;

import com.mongodb.client.gridfs.model.GridFSFile;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.bson.Document;
import org.bson.types.ObjectId;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.gridfs.GridFsTemplate;
import org.springframework.stereotype.Component;

/**
 * GridFS(파일 본문 chunk 저장/스트리밍)는 Querydsl 대상이 아니라서 GridFsTemplate 으로 한다.
 * <p>
 * 채팅 첨부파일 저장소 (MongoDB GridFS, 버킷 fs).
 * metadata = {channelId, uploaderId, contentType, originalName, size}
 */
@Component
public class ChatFileStore {

    static final String CHANNEL_ID = "channelId";
    static final String UPLOADER_ID = "uploaderId";
    static final String CONTENT_TYPE = "contentType";
    static final String ORIGINAL_NAME = "originalName";
    static final String SIZE = "size";

    private final GridFsTemplate gridFsTemplate;

    public ChatFileStore(GridFsTemplate chatGridFsTemplate) {
        this.gridFsTemplate = chatGridFsTemplate;
    }

    /**
     * 파일을 저장한다 (본문은 chunk 단위로 흘려 넣으므로 전체를 메모리에 올리지 않는다)
     */
    public ChatFile store(InputStream content, Long channelId, Long uploaderId, String contentType, String originalName, long size) {
        Document metadata = new Document(CHANNEL_ID, channelId)
            .append(UPLOADER_ID, uploaderId)
            .append(CONTENT_TYPE, contentType)
            .append(ORIGINAL_NAME, originalName)
            .append(SIZE, size);
        ObjectId fileId = gridFsTemplate.store(content, originalName, contentType, metadata);
        return new ChatFile(fileId.toHexString(), channelId, uploaderId, contentType, originalName, size);
    }

    /**
     * 내려받기용 : metadata 를 먼저 주고, 본문은 권한 확인 뒤 {@link ChatFileDownload#open()} 할 때 연다 (조회 1번).
     * 형식이 틀린 ID 나 없는 파일이면 empty
     */
    public Optional<ChatFileDownload> findForDownload(String fileId) {
        if (fileId == null || !ObjectId.isValid(fileId)) {
            return Optional.empty();
        }
        return Optional.ofNullable(gridFsTemplate.findOne(Query.query(Criteria.where("_id").is(new ObjectId(fileId)))))
            .map(file -> new ChatFileDownload(toChatFile(file), () -> gridFsTemplate.getResource(file)));
    }

    /**
     * 여러 파일의 metadata 를 한 번에 (순서는 보장하지 않는다). 형식이 틀린 ID 는 무시
     */
    public List<ChatFile> findAllById(Collection<String> fileIds) {
        List<ObjectId> ids = fileIds.stream().filter(ObjectId::isValid).map(ObjectId::new).toList();
        if (ids.isEmpty()) {
            return List.of();
        }
        return gridFsTemplate.find(Query.query(Criteria.where("_id").in(ids)))
            .map(ChatFileStore::toChatFile)
            .into(new ArrayList<>());
    }

    private static ChatFile toChatFile(GridFSFile file) {
        Document metadata = file.getMetadata() == null ? new Document() : file.getMetadata();
        Number channelId = metadata.get(CHANNEL_ID, Number.class);
        Number uploaderId = metadata.get(UPLOADER_ID, Number.class);
        return new ChatFile(
            file.getObjectId().toHexString(),
            channelId == null ? null : channelId.longValue(),
            uploaderId == null ? null : uploaderId.longValue(),
            metadata.getString(CONTENT_TYPE),
            metadata.getString(ORIGINAL_NAME),
            file.getLength());
    }
}
