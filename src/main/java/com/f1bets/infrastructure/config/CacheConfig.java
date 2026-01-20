package com.f1bets.infrastructure.config;

import com.f1bets.application.dto.EventWithDrivers;
import com.f1bets.application.dto.SessionQuery;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;
import java.util.Optional;
import java.util.concurrent.TimeUnit;

@Configuration
public class CacheConfig {

    @Bean
    public Cache<SessionQuery, List<EventWithDrivers>> sessionCache(
            @Value("${openf1.cache-ttl:180}") int cacheTtlSeconds) {
        return Caffeine.newBuilder()
            .expireAfterWrite(cacheTtlSeconds, TimeUnit.SECONDS)
            .maximumSize(1000)
            .build();
    }

    @Bean
    public Cache<Integer, Optional<EventWithDrivers>> sessionKeyCache(
            @Value("${openf1.cache-ttl:180}") int cacheTtlSeconds) {
        return Caffeine.newBuilder()
            .expireAfterWrite(cacheTtlSeconds, TimeUnit.SECONDS)
            .maximumSize(5000)
            .build();
    }
}
