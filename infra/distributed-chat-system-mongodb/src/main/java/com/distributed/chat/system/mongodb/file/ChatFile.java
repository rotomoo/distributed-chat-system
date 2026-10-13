package com.distributed.chat.system.mongodb.file;

/**
 * 채팅 첨부파일 (GridFS). 파일 본문은 GridFS chunks, 아래 값은 fs.files 의 metadata
 *
 * @param fileId     GridFS 파일 ID (ObjectId hex)
 * @param channelId  올린 채널 (이 채널 멤버만 내려받을 수 있다)
 * @param uploaderId 올린 사람 (메시지에 붙일 수 있는 건 올린 사람뿐)
 */
public record ChatFile(String fileId, Long channelId, Long uploaderId, String contentType, String originalName, long size) {
}
