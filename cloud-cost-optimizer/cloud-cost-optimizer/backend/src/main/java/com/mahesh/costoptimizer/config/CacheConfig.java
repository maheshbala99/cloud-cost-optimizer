package com.mahesh.costoptimizer.config;

import com.github.benmanes.caffeine.cache.Caffeine;
import org.springframework.cache.CacheManager;
import org.springframework.cache.caffeine.CaffeineCacheManager;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.concurrent.TimeUnit;

/**
 * Cache configuration using Caffeine in-memory cache.
 *
 * Caching is important here because:
 *  - AWS Cost Explorer API charges per request (~$0.01/request)
 *  - CloudWatch API has rate limits
 *  - Cost data doesn't change more frequently than once per hour
 *
 * Cache TTLs:
 *  - costSummary: 1 hour (full dashboard data)
 *  - costHistory: 6 hours (historical data changes slowly)
 *  - recommendations: 4 hours (EC2 analysis is expensive)
 */
@Configuration
public class CacheConfig {

    @Bean
    public CacheManager cacheManager() {
        CaffeineCacheManager cacheManager = new CaffeineCacheManager(
                "costSummary", "costHistory", "recommendations");

        cacheManager.setCaffeine(Caffeine.newBuilder()
                .expireAfterWrite(1, TimeUnit.HOURS)
                .maximumSize(100)
                .recordStats());

        return cacheManager;
    }
}
