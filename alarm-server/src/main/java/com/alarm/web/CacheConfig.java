package com.alarm.web;

import com.alarm.contracts.api.RecommendationPreviewResponse;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.data.redis.cache.RedisCacheConfiguration;
import org.springframework.data.redis.cache.RedisCacheManager;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.serializer.Jackson2JsonRedisSerializer;
import org.springframework.data.redis.serializer.RedisSerializationContext;
import org.springframework.data.redis.serializer.StringRedisSerializer;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.http.converter.json.Jackson2ObjectMapperBuilder;

import java.time.Duration;

/**
 * Redis 读缓存（仅 k8s profile 启用）。
 * 只缓存推荐预览（advisory 只读接口），TTL 15s 兜底失效；强一致写路径（警单/派遣/幂等）一律直查 MySQL。
 * 序列化用注册了 JavaTimeModule 的共享 ObjectMapper（record + Instant 兼容），key 用字符串。
 */
@Configuration
@EnableCaching
@Profile("k8s")
public class CacheConfig {

    public static final String CACHE_RECOMMENDATION_PREVIEW = "recommendation-preview";
    public static final Duration PREVIEW_TTL = Duration.ofSeconds(15);

    @Bean
    public RedisCacheManager cacheManager(RedisConnectionFactory connectionFactory,
                                          Jackson2ObjectMapperBuilder mapperBuilder) {
        com.fasterxml.jackson.databind.ObjectMapper mapper = mapperBuilder.build();
        RedisCacheConfiguration config = RedisCacheConfiguration.defaultCacheConfig()
                .entryTtl(PREVIEW_TTL)
                .disableCachingNullValues()
                .serializeKeysWith(RedisSerializationContext.SerializationPair
                        .fromSerializer(new StringRedisSerializer()))
                .serializeValuesWith(RedisSerializationContext.SerializationPair
                        .fromSerializer(new Jackson2JsonRedisSerializer<>(mapper, RecommendationPreviewResponse.class)));
        return RedisCacheManager.builder(connectionFactory)
                .cacheDefaults(config)
                .build();
    }
}
