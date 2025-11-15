-- =====================================================================
-- V2__seed_app_config.sql — 외부 API URL/timeout과 스케줄 주기는 YAML에서 관리
-- =====================================================================

SET NAMES utf8mb4;
SET time_zone = '+00:00';

-- ---------------------------------------------------------------------
-- 1) 기본 반경 / 후보 K / ETA 캐시
-- ---------------------------------------------------------------------
INSERT INTO app_config (cfg_key, cfg_value, description) VALUES
('default-radius-km', '15',  '병원 탐색 기본 반경(km)'),
('CANDIDATE_K',       '10',  '후보 병원 최대 K(권장 10)'),
('CANDIDATE_MIN',     '5',   '반경을 넘어도 최소 확보할 후보 수(Kmin)'),
('ETA_CACHE_MIN',     '2',   'ETA 결과 캐시 유지시간(분). Caffeine 설정과 맞출 것')
ON DUPLICATE KEY UPDATE
  cfg_value = VALUES(cfg_value),
  description = VALUES(description);

-- ---------------------------------------------------------------------
-- 2) 데이터 신선도 임계값
-- ---------------------------------------------------------------------
INSERT INTO app_config (cfg_key, cfg_value, description) VALUES
('STALE_SOFT_SEC',     '300',  '5분. 이하면 FRESH, 초과 시 SOFT로 감점 적용'),
('STALE_HARD_SEC',     '3600', '60분. 초과 시 HARD → GRAY 처리(무효)'),
('STALE_HLI_EXPIRE_H', '2',    'HLI 스냅샷 자체 TTL(시간). 기준 초과 시 무시')
ON DUPLICATE KEY UPDATE
  cfg_value = VALUES(cfg_value),
  description = VALUES(description);

-- ---------------------------------------------------------------------
-- 3) HLI 점수 가중치 / 패널티 (예비 포함)
-- ---------------------------------------------------------------------
INSERT INTO app_config (cfg_key, cfg_value, description) VALUES
('HLI_BASE_ER_WEIGHT',    '0.7',  'ER 지표 비중(예비)'),
('HLI_BASE_ICU_WEIGHT',   '0.3',  'ICU 지표 비중(예비)'),
('HLI_LAG_SOFT_PENALTY',  '15',   'SOFT 지연 구간 점수 감점'),
('HLI_LAG_HARD_SCORE',    '100',  'HARD일 때 강제 점수(예비, GRAY 처리용)'),
('HLI_BUCKET_LOW_BOUND',  '3',    'ICU 여유 3병상 이하 = CRITICAL_LOW 시작(예비)'),
('HLI_BUCKET_HIGH_BOUND', '10',   'ICU 여유 10병상 이상 = HIGH 시작(예비)'),
('HLI_SCORE_CLAMP_MIN',   '0',    '점수 하한(예비)'),
('HLI_SCORE_CLAMP_MAX',   '100',  '점수 상한(예비)')
ON DUPLICATE KEY UPDATE
  cfg_value = VALUES(cfg_value),
  description = VALUES(description);

-- ---------------------------------------------------------------------
-- 4) 재난문자 TTL (시간 단위)
-- ---------------------------------------------------------------------
INSERT INTO app_config (cfg_key, cfg_value, description) VALUES
('ALERT_TTL_BLOCK_H',  '4', 'BLOCK급(통제, 대피 등) 유효 시간'),
('ALERT_TTL_WARN_H',   '2', 'WARN급(주의, 경보 등) 유효 시간'),
('ALERT_TTL_IGNORE_H', '1', 'IGNORE급(기타 정보) 유효 시간')
ON DUPLICATE KEY UPDATE
  cfg_value = VALUES(cfg_value),
  description = VALUES(description);

-- ---------------------------------------------------------------------
-- 5) 경보 필터 정책
-- ---------------------------------------------------------------------
INSERT INTO app_config (cfg_key, cfg_value, description) VALUES
('ALERT_BLOCK_EXCLUDE',        '1',       'BLOCK 지역 병원 제외 (1=활성)'),
('ALERT_WARN_BADGE',           '1',       'WARN 지역 배지 표시 (1=활성)'),
('ALERT_WARN_PENALTY',         '5',       'WARN 지역 가중 감점(점)'),
('ALERT_MATCH_FALLBACK_LEVEL', 'SIGUNGU', '매칭 실패 시 기본 level(구 단위)')
ON DUPLICATE KEY UPDATE
  cfg_value = VALUES(cfg_value),
  description = VALUES(description);

-- ---------------------------------------------------------------------
-- 6) 추천 정렬 기준 / TopN
-- ---------------------------------------------------------------------
INSERT INTO app_config (cfg_key, cfg_value, description) VALUES
('RANK_PRIMARY',   'level',    '최우선 정렬 기준 (level | score)'),
('RANK_SECONDARY', 'score',    '2차 정렬 (score | distance)'),
('RANK_TIEBREAK',  'distance', '동점 시 거리순'),
('RANK_TOPN',      '3',        '상위 추천 병원 개수(기본 3)')
ON DUPLICATE KEY UPDATE
  cfg_value = VALUES(cfg_value),
  description = VALUES(description);

-- ---------------------------------------------------------------------
-- 7) 운영 보조 키
-- ---------------------------------------------------------------------
INSERT INTO app_config (cfg_key, cfg_value, description) VALUES
('MAINTENANCE_MODE', '0',    '1=정지(일부 차단), 0=정상'),
('LOG_LEVEL',        'INFO', '서비스 로그 레벨(예: DEBUG/INFO/WARN/ERROR)'),
('ADMIN_CONTACT',    '',     '운영자 연락처(메타)')
ON DUPLICATE KEY UPDATE
  cfg_value = VALUES(cfg_value),
  description = VALUES(description);
