package com.distributed.chat.system.mongodb.collection;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public class Post {

    private Long id;
    private Long userId;
    private String imageUrl;
    private String content;
}
