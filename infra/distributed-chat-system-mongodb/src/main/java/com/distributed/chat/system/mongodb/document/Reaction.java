package com.distributed.chat.system.mongodb.document;

import java.util.Set;
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
@Document("reaction")
public class Reaction {

    @Id
    private String id;
    private String chatMessageId;
    private String emoji;
    private Set<Long> userIds;
}
