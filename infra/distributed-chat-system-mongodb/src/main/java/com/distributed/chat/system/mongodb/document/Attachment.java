package com.distributed.chat.system.mongodb.document;

import com.distributed.chat.system.mongodb.enums.AttachmentType;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Builder
@Document("attachment")
public class Attachment {

    @Id
    private String id;
    private String chatMessageId;
    private AttachmentType attachmentType;
    private String url;
}
