package com.distributed.chat.system.client.api.web.controller.v1;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/v1/private/api/redis/test")
@RequiredArgsConstructor
@Slf4j
public class RedisTestController {

    private final RedisTemplate redisTemplate;

    @GetMapping("/read/{key}")
    public ResponseEntity<?> getKeyValue(@PathVariable String key) {
        String value = (String) redisTemplate.opsForValue().get(key);

        return new ResponseEntity<>(value, HttpStatus.OK);
    }

    @PostMapping("/create/{key}/{value}")
    public ResponseEntity<?> createKeyValue(@PathVariable String key, @PathVariable String value) {
        redisTemplate.opsForValue().set(key, value);

        return new ResponseEntity<>(null, HttpStatus.OK);
    }
}
