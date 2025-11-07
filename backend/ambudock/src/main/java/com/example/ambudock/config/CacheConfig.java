package com.example.ambudock.config;

import com.github.benmanes.caffeine.cache.Caffeine;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.cache.caffeine.CaffeineCache;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.cache.support.SimpleCacheManager;

import java.time.Duration;
import java.util.Arrays;

@Configuration
@EnableCaching
public class CacheConfig {

    @Bean
    public CacheManager cacheManager() {

        // 병원 현황(HLI/NMC) 캐시: 60초 유지
        CaffeineCache hospitals = new CaffeineCache(
                "hospitals",
                Caffeine.newBuilder()
                        .maximumSize(5_000)
                        .expireAfterWrite(Duration.ofSeconds(60))  // TTL
                        .recordStats()
                        .build()
        );

        // 재난문자(MOIS) 캐시: 180초 유지
        CaffeineCache disasters = new CaffeineCache(
                "disasters",
                Caffeine.newBuilder()
                        .maximumSize(5_000) // 캐시에 최대 5,000개까지만 저장(넘치면 덜 쓰인 항목부터 자동 제거)
                        .expireAfterWrite(Duration.ofSeconds(180))
                        .recordStats()
                        .build()
        );

        // TMAP ETA/거리 캐시: 120s (동일 경로 반복 호출 방지)
        CaffeineCache eta = new CaffeineCache(
                "eta",
                Caffeine.newBuilder()
                        .maximumSize(10_000)
                        .expireAfterWrite(Duration.ofSeconds(120))
                        .recordStats()
                        .build()
        );

        // AppConfig 읽기 캐시: 10s 단기 (DB 조회 완화)
        CaffeineCache appconfig = new CaffeineCache(
                "appconfig",
                Caffeine.newBuilder()
                        .maximumSize(1_000)
                        .expireAfterWrite(Duration.ofSeconds(10))   // 운영 파라미터 단기 캐시
                        .recordStats()
                        .build()
        );

        SimpleCacheManager m = new SimpleCacheManager();
        // 위에서 만든 캐시들(hospitals, disasters, eta, appconfig)을 매니저에 등록
        // → 이름으로 @Cacheable("eta") 등 호출 시 해당 캐시를 사용 가능
        m.setCaches(Arrays.asList(hospitals, disasters, eta, appconfig));
        return m;
    }
}
