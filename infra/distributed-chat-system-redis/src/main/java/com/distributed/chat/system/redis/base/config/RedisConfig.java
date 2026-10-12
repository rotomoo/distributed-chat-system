package com.distributed.chat.system.redis.base.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.lettuce.core.ReadFrom;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.autoconfigure.data.redis.LettuceClientConfigurationBuilderCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.serializer.GenericJackson2JsonRedisSerializer;
import org.springframework.data.redis.serializer.StringRedisSerializer;

/**
 * 접속 정보(standalone / sentinel)는 spring.data.redis.* 프로퍼티로 결정 (application-common.yml)
 */
@Configuration
public class RedisConfig {

    /**
     * 읽기는 replica 우선, replica 가 없으면 master.
     * docker 프로필(sentinel + replica 가 같은 네트워크)에서만 켠다. 호스트(IDE, 테스트)에서는 replica 의 컨테이너 내부 IP 에
     * 닿을 수 없어서 replica 읽기를 켜면 연결이 실패한다.
     */
    @Bean
    @ConditionalOnProperty(name = "chat.redis.read-from-replica", havingValue = "true")
    public LettuceClientConfigurationBuilderCustomizer readFromReplicaCustomizer() {
        return builder -> builder.readFrom(ReadFrom.REPLICA_PREFERRED);
    }

    @Bean
    public RedisTemplate<String, Object> redisTemplate(RedisConnectionFactory redisConnectionFactory,
        ObjectMapper objectMapper) {
        final RedisTemplate<String, Object> template = new RedisTemplate<>();
        template.setConnectionFactory(redisConnectionFactory);

        StringRedisSerializer stringRedisSerializer = new StringRedisSerializer();
        GenericJackson2JsonRedisSerializer jackson2JsonRedisSerializer = new GenericJackson2JsonRedisSerializer(
            objectMapper);

        template.setKeySerializer(stringRedisSerializer);
        template.setHashKeySerializer(stringRedisSerializer);

        template.setValueSerializer(jackson2JsonRedisSerializer);
        template.setHashValueSerializer(jackson2JsonRedisSerializer);
        return template;
    }
}
