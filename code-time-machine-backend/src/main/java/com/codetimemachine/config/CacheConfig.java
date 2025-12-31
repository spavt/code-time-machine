package com.codetimemachine.config;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Duration;

/**
 * 缓存配置 - 使用 Caffeine 高性能缓存
 */
@Configuration
public class CacheConfig {

    /**
     * 文件内容缓存
     * - 最大 1000 条记录
     * - 1 小时后过期
     * - 记录统计信息
     */
    @Bean
    public Cache<String, String> fileContentCache() {
        return Caffeine.newBuilder()
                .maximumSize(1000)
                .expireAfterWrite(Duration.ofHours(1))
                .recordStats()
                .build();
    }

    /**
     * 文件时间线缓存（用于 /file/timeline 接口）
     */
    @Bean
    public Cache<String, Object> timelineCache() {
        return Caffeine.newBuilder()
                .maximumSize(200)
                .expireAfterWrite(Duration.ofMinutes(30))
                .recordStats()
                .build();
    }
}
