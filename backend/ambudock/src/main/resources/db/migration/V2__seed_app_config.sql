-- =====================================================================
-- V2__seed_app_config.sql (FINAL) — extended
-- AmbuDock 초기 AppConfig 데이터 삽입 (MySQL 8.0)
-- =====================================================================

SET NAMES utf8mb4;
SET time_zone = '+00:00';

-- ---------------------------------------------------------------------
-- 0) 외부 API 엔드포인트 / 타임아웃 (추가)
--    -> NMC / MOIS (스케줄러에서 사용), TMAP 프록시(이미 존재)
-- ---------------------------------------------------------------------
INSERT INTO app_config (cfg_key, cfg_value, description) VALUES
('NMC_API_URL',            'http://localhost:9000', 'NMC(전국응급의료기관) API 기본 URL'),
('NMC_API_TIMEOUT_MS',     '1500',                 'NMC API 호출 타임아웃(ms)'),
('MOIS_API_URL',           'http://localhost:9010', 'MOIS(행안부 재난문자) API 기본 URL'),
('MOIS_API_TIMEOUT_MS',    '2000',                 'MOIS API 호출 타임아웃(ms)')
ON DUPLICATE KEY UPDATE
  cfg_value = VALUES(cfg_value),
  description = VALUES(description);

-- ---------------------------------------------------------------------
-- 1) 기본 반경 / 후보 K / TMAP 프록시 / ETA 캐시 (신규)
-- ---------------------------------------------------------------------
INSERT INTO app_config (cfg_key, cfg_value, description) VALUES
('default-radius-km',      '15',                   '병원 탐색 기본 반경(km)'),
('CANDIDATE_K',            '10',                   '후보 병원 최대 K(권장 10)'),
('CANDIDATE_MIN',          '5',                    '반경을 넘어도 최소 확보할 후보 수(Kmin)'),
('TMAP_PROXY_URL',         'http://localhost:8080','TMAP 라우팅 프록시 Base URL'),
('TMAP_PROXY_TIMEOUT_MS',  '1500',                 'TMAP 프록시 타임아웃(ms)'),
('ETA_CACHE_MIN',          '2',                    'ETA 결과 캐시 유지시간(분). Caffeine 설정과 맞출 것')
ON DUPLICATE KEY UPDATE
  cfg_value = VALUES(cfg_value),
  description = VALUES(description);

-- ---------------------------------------------------------------------
-- 2) 데이터 신선도 임계값
-- ---------------------------------------------------------------------
INSERT INTO app_config (cfg_key, cfg_value, description) VALUES
('STALE_SOFT_SEC',       '300',  '5분. 이 이하이면 FRESH, 초과 시 SOFT로 감점 적용'),
('STALE_HARD_SEC',       '3600', '60분. 초과 시 HARD → GRAY 처리(무효)'),
('STALE_HLI_EXPIRE_H',   '2',    'HLI 데이터 자체 TTL(시간 단위) — 2시간 이상 지난 스냅샷은 무시')
ON DUPLICATE KEY UPDATE
  cfg_value = VALUES(cfg_value),
  description = VALUES(description);

-- ---------------------------------------------------------------------
-- 3) HLI 점수 가중치 / 패널티  (예비 설정 포함)
-- ---------------------------------------------------------------------
INSERT INTO app_config (cfg_key, cfg_value, description) VALUES
('HLI_BASE_ER_WEIGHT',   '0.7',  'ER 병상 지표 비중(예비)'),
('HLI_BASE_ICU_WEIGHT',  '0.3',  'ICU 병상 지표 비중(예비)'),
('HLI_LAG_SOFT_PENALTY', '15',   'SOFT 지연 시 점수 감점량(15)'),
('HLI_LAG_HARD_SCORE',   '100',  'HARD일 때 강제 점수 (GRAY 처리용, 예비)'),
('HLI_BUCKET_LOW_BOUND', '3',    'ICU 여유 3병상 이하 = CRITICAL_LOW 시작 기준(예비)'),
('HLI_BUCKET_HIGH_BOUND','10',   'ICU 여유 10병상 이상 = HIGH 시작 기준(예비)'),
('HLI_SCORE_CLAMP_MIN',  '0',    '점수 하한 (최소값, 예비)'),
('HLI_SCORE_CLAMP_MAX',  '100',  '점수 상한 (최대값, 예비)')
ON DUPLICATE KEY UPDATE
  cfg_value = VALUES(cfg_value),
  description = VALUES(description);

-- ---------------------------------------------------------------------
-- 4) 재난문자 TTL (시간 단위)
-- ---------------------------------------------------------------------
INSERT INTO app_config (cfg_key, cfg_value, description) VALUES
('ALERT_TTL_BLOCK_H',    '4',    'BLOCK급(통제, 대피 등) 경보의 유효 시간 (4시간)'),
('ALERT_TTL_WARN_H',     '2',    'WARN급(주의, 경보 등) 경보의 유효 시간 (2시간)'),
('ALERT_TTL_IGNORE_H',   '1',    'IGNORE급(기타 정보) TTL (1시간)')
ON DUPLICATE KEY UPDATE
  cfg_value = VALUES(cfg_value),
  description = VALUES(description);

-- ---------------------------------------------------------------------
-- 5) 경보 필터 정책
-- ---------------------------------------------------------------------
INSERT INTO app_config (cfg_key, cfg_value, description) VALUES
('ALERT_BLOCK_EXCLUDE',        '1',      'BLOCK 지역의 병원은 추천 결과에서 제외 (1=활성)'),
('ALERT_WARN_BADGE',           '1',      'WARN 지역의 병원은 카드 배지로 표시 (1=활성)'),
('ALERT_WARN_PENALTY',         '5',      'WARN 지역 병원에 가중 감점 적용 시 값(5)'),
('ALERT_MATCH_FALLBACK_LEVEL', 'SIGUNGU','매칭 실패 시 기본 match_level (기본 구 단위)')
ON DUPLICATE KEY UPDATE
  cfg_value = VALUES(cfg_value),
  description = VALUES(description);

-- ---------------------------------------------------------------------
-- 6) 추천 정렬 기준 / TopN
-- ---------------------------------------------------------------------
INSERT INTO app_config (cfg_key, cfg_value, description) VALUES
('RANK_PRIMARY',   'level',    '최우선 정렬 기준 (‘level’ or ‘score’)'),
('RANK_SECONDARY', 'score',    '2차 정렬 (‘score’, ‘distance’)'),
('RANK_TIEBREAK',  'distance', '동점 시 거리순 정렬'),
('RANK_TOPN',      '3',        '상위 추천 병원 개수 (기본 3)')
ON DUPLICATE KEY UPDATE
  cfg_value = VALUES(cfg_value),
  description = VALUES(description);

-- ---------------------------------------------------------------------
-- 7) 시스템 동작 주기
-- ---------------------------------------------------------------------
INSERT INTO app_config (cfg_key, cfg_value, description) VALUES
('FETCH_INTERVAL_SEC',          '60',  'NMC 실시간 API 데이터 수집 주기(초)'),
('HLI_CALC_INTERVAL_SEC',       '60',  'HLI 계산 주기(초)'),
('ALERT_SYNC_INTERVAL_SEC',     '180', '재난문자 갱신 주기(초)'),
('RANK_REFRESH_INTERVAL_SEC',   '60',  '사용자 위치 기준 재계산 주기(초)')
ON DUPLICATE KEY UPDATE
  cfg_value = VALUES(cfg_value),
  description = VALUES(description);

-- ---------------------------------------------------------------------
-- 8) 운영 보조 키(권장)
--    - AppConfig로 관리하면 운영 중 값 변경이 쉬움
-- ---------------------------------------------------------------------
INSERT INTO app_config (cfg_key, cfg_value, description) VALUES
('MAINTENANCE_MODE',          '0',    '운영 모드: 1=정지(서비스 일부 차단), 0=정상'),
('LOG_LEVEL',                  'INFO', '서비스 로그 레벨(예: DEBUG/INFO/WARN/ERROR)'),
('ADMIN_CONTACT',              '',     '운영자 연락처 (운영용 메타)')
ON DUPLICATE KEY UPDATE
  cfg_value = VALUES(cfg_value),
  description = VALUES(description);
