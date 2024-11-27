package com.distributed.chat.system.chatting.client;

import com.distributed.chat.system.mongodb.collection.Post;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;

@Component
public class PostClient {

    private final Map<Long, Post> posts = new HashMap<>();

    public PostClient() {
        // User 1 posts
        posts.put(1L, new Post(1L, 1L, "imageUrl1", "content1"));
        posts.put(2L, new Post(2L, 1L, "imageUrl2", "content2"));
        posts.put(3L, new Post(3L, 1L, "imageUrl3", "content3"));
        posts.put(4L, new Post(4L, 1L, "imageUrl4", "content4"));
        posts.put(5L, new Post(5L, 1L, "imageUrl5", "content5"));

        // User 2 posts
        posts.put(6L, new Post(6L, 2L, "imageUrl6", "content6"));
        posts.put(7L, new Post(7L, 2L, "imageUrl7", "content7"));
        posts.put(8L, new Post(8L, 2L, "imageUrl8", "content8"));
        posts.put(9L, new Post(9L, 2L, "imageUrl9", "content9"));
        posts.put(10L, new Post(10L, 2L, "imageUrl10", "content10"));
    }

    public Post getPost(Long id) {
        return posts.get(id);
    }
}
