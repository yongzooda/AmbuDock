package com.example.ambudock.config;

import com.github.benmanes.caffeine.cache.Caffeine;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.cache.caffeine.CaffeineCacheManager;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableCaching // Spring의 캐시 기능(@Cacheable)을 전역적으로 활성화
public class CacheConfig {
    @Bean
    public CacheManager cacheManager() {

        // Caffeine 기반 캐시 매니저 생성
        // 등록된 캐시 이름("hospitals", "disasters", "eta")들은 @Cacheable에서 사용할 수 있음
        CaffeineCacheManager m = new CaffeineCacheManager("hospitals","disasters","eta");
        m.setCaffeine(Caffeine.newBuilder().maximumSize(5000)); // 캐시의 최대 저장 개수를 5000개로 제한
        return m;
    }
}
