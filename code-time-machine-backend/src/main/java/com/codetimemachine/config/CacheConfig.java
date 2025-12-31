package com.codetimemachine.config;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Duration;

@Configuration
public class CacheConfig {

    @Bean
    public Cache<String, String> fileContentCache() {
        return Caffeine.newBuilder()
                .maximumSize(1000)
                .expireAfterWrite(Duration.ofHours(1))
                .recordStats()
                .build();
    }

    @Bean
    public Cache<String, Object> timelineCache() {
        return Caffeine.newBuilder()
                .maximumSize(200)
                .expireAfterWrite(Duration.ofMinutes(30))
                .recordStats()
                .build();
    }
}
