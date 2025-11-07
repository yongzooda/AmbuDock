package com.example.ambudock.service;

import jakarta.persistence.EntityManager;
import jakarta.persistence.NoResultException;
import lombok.RequiredArgsConstructor;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class AppConfigService {

    private final EntityManager em;

    /**
     * AppConfig 조회
     * - DB(app_config)에서 cfg_key로 값을 읽어옴
     * - 10초 TTL의 "appconfig" 캐시(캐시 구성은 CacheConfig)로 조회 부하 감소
     * - 값 없으면 def 반환
     */
    @Transactional(readOnly = true)
    @Cacheable(cacheNames = "appconfig", key = "#key")
    public String get(String key, String def) {
        try {
            Object v = em.createNativeQuery("select cfg_value from app_config where cfg_key = :k")
                    .setParameter("k", key)
                    .getSingleResult();
            return v != null ? v.toString() : def;
        } catch (NoResultException e) {
            return def; // 키가 없을 때 기본값
        }
    }

    /**
     * int 전용 헬퍼
     * - get()으로 읽고 정수 변환
     * - 파싱 실패/누락 시 def 반환
     */
    @Transactional(readOnly = true)
    public int getInt(String key, int def) {
        String s = get(key, null);
        if (s == null) return def;
        try {
            return Integer.parseInt(s.trim());
        } catch (NumberFormatException e) {
            return def;
        }
    }

    /**
     * 특정 키 캐시 무효화
     * - 운영자/관리자 API에서 DB 값을 갱신한 다음 호출
     * - 다음 조회 때 DB를 다시 읽어 최신값 반영
     */
    @Transactional
    @CacheEvict(cacheNames = "appconfig", key = "#key")
    public void evict(String key) {
        // DB 값 갱신은 별도 Admin 서비스/리포지토리에서 수행 후 이 메서드 호출
    }
}
