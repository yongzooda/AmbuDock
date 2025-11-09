package com.example.ambudock.config;

import com.github.benmanes.caffeine.cache.Caffeine;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.cache.caffeine.CaffeineCache;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.cache.support.SimpleCacheManager;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;

import java.time.Duration;
import java.util.Arrays;

/**
 * Cache 설정 — app_config의 시스템 주기 키들을 참고하여 TTL 결정
 *
 * 매핑:
 *  - hospitals TTL   -> HLI_CALC_INTERVAL_SEC (초)
 *  - disasters TTL   -> ALERT_SYNC_INTERVAL_SEC (초)
 *  - eta TTL         -> ETA_CACHE_MIN (분)
 *  - appconfig TTL   -> APPCONFIG_CACHE_SEC (초, 기본 10)
 *
 */
@Configuration
@EnableCaching
public class CacheConfig {

    private static final Logger log = LoggerFactory.getLogger(CacheConfig.class);

    private final JdbcTemplate jdbc;

    public CacheConfig(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Bean
    public CacheManager cacheManager() {
        // HLI 계산 주기 기준으로 hospitals 캐시 TTL 결정 (초 단위)
        int hliCalcIntervalSec = getIntConfig("HLI_CALC_INTERVAL_SEC", 60);
        int hospitalsTtlSec = Math.max(1, hliCalcIntervalSec);

        // 재난문자 갱신 주기 기준으로 disasters 캐시 TTL 결정 (초 단위)
        int alertSyncIntervalSec = getIntConfig("ALERT_SYNC_INTERVAL_SEC", 180);
        int disastersTtlSec = Math.max(1, alertSyncIntervalSec);

        // ETA 캐시 (분 단위 키)
        int etaCacheMin = getIntConfig("ETA_CACHE_MIN", 2);
        long etaTtlSec = Math.max(1, etaCacheMin) * 60L;

        // AppConfig 캐시 TTL (초)
        int appconfigTtlSec = getIntConfig("APPCONFIG_CACHE_SEC", 10);

        log.info("Initializing caches: hospitalsTtl={}s (HLI_CALC_INTERVAL_SEC), disastersTtl={}s (ALERT_SYNC_INTERVAL_SEC), etaTtl={}s (ETA_CACHE_MIN), appconfigTtl={}s",
                hospitalsTtlSec, disastersTtlSec, etaTtlSec, appconfigTtlSec);

        // --- Cache 생성 ---
        CaffeineCache hospitals = new CaffeineCache(
                "hospitals",
                Caffeine.newBuilder()
                        .maximumSize(5_000)
                        .expireAfterWrite(Duration.ofSeconds(hospitalsTtlSec))
                        .recordStats()
                        .build()
        );

        CaffeineCache disasters = new CaffeineCache(
                "disasters",
                Caffeine.newBuilder()
                        .maximumSize(5_000)
                        .expireAfterWrite(Duration.ofSeconds(disastersTtlSec))
                        .recordStats()
                        .build()
        );

        CaffeineCache eta = new CaffeineCache(
                "eta",
                Caffeine.newBuilder()
                        .maximumSize(10_000)
                        .expireAfterWrite(Duration.ofSeconds(etaTtlSec))
                        .recordStats()
                        .build()
        );

        CaffeineCache appconfig = new CaffeineCache(
                "appconfig",
                Caffeine.newBuilder()
                        .maximumSize(1_000)
                        .expireAfterWrite(Duration.ofSeconds(appconfigTtlSec))
                        .recordStats()
                        .build()
        );

        SimpleCacheManager manager = new SimpleCacheManager();
        manager.setCaches(Arrays.asList(hospitals, disasters, eta, appconfig));
        return manager;
    }

    /**
     * app_config에서 단일 키의 정수값을 읽음. (없거나 파싱 실패 시 기본값 반환)
     */
    private int getIntConfig(String key, int defaultVal) {
        try {
            String sql = "SELECT cfg_value FROM app_config WHERE cfg_key = ? LIMIT 1";
            String val = jdbc.queryForObject(sql, new Object[]{key}, String.class);
            if (val == null) return defaultVal;
            val = val.trim();
            return Integer.parseInt(val);
        } catch (EmptyResultDataAccessException ex) {
            // 키가 없음
            return defaultVal;
        } catch (NumberFormatException ex) {
            log.warn("Invalid integer for app_config key '{}', returning default {}", key, defaultVal);
            return defaultVal;
        } catch (Exception ex) {
            log.error("Error reading app_config key '{}', returning default {}", key, defaultVal, ex);
            return defaultVal;
        }
    }
}
