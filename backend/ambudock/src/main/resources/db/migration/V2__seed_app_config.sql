-- =====================================================================
-- V2__seed_app_config.sql
-- AmbuDock 초기 AppConfig 데이터 삽입 (MySQL 8.0)
-- 작성일: 2025-10-13
-- 설명: 데이터 신선도 / HLI 점수 / 재난문자 TTL / 추천 정책 / 주기 설정
-- =====================================================================

SET NAMES utf8mb4;
SET time_zone = '+00:00';

-- ---------------------------------------------------------------------
-- 1) 데이터 신선도 임계값
-- ---------------------------------------------------------------------
INSERT INTO app_config (cfg_key, cfg_value, description) VALUES
('STALE_SOFT_SEC',       '300',  '5분. 이 이하이면 FRESH, 초과 시 SOFT로 감점 적용'),
('STALE_HARD_SEC',       '3600', '60분. 초과 시 HARD → GRAY 처리(무효)'),
('STALE_HLI_EXPIRE_H',   '2',    'HLI 데이터 자체 TTL(시간 단위) — 2시간 이상 지난 스냅샷은 무시')
ON DUPLICATE KEY UPDATE
  cfg_value = VALUES(cfg_value),
  description = VALUES(description);

-- ---------------------------------------------------------------------
-- 2) HLI 점수 가중치 / 패널티
-- ---------------------------------------------------------------------
INSERT INTO app_config (cfg_key, cfg_value, description) VALUES
('HLI_BASE_ER_WEIGHT',   '0.7',  'ER 병상 지표 비중'),
('HLI_BASE_ICU_WEIGHT',  '0.3',  'ICU 병상 지표 비중'),
('HLI_LAG_SOFT_PENALTY', '15',   'SOFT 지연 시 점수 감점량(15)'),
('HLI_LAG_HARD_SCORE',   '100',  'HARD일 때 강제 점수 (GRAY 처리용)'),
('HLI_BUCKET_LOW_BOUND', '3',    'ICU 여유 3병상 이하 = CRITICAL_LOW 시작 기준'),
('HLI_BUCKET_HIGH_BOUND','10',   'ICU 여유 10병상 이상 = HIGH 시작 기준'),
('HLI_SCORE_CLAMP_MIN',  '0',    '점수 하한 (최소값)'),
('HLI_SCORE_CLAMP_MAX',  '100',  '점수 상한 (최대값)')
ON DUPLICATE KEY UPDATE
  cfg_value = VALUES(cfg_value),
  description = VALUES(description);

-- ---------------------------------------------------------------------
-- 3) 재난문자 TTL (시간 단위)
-- ---------------------------------------------------------------------
INSERT INTO app_config (cfg_key, cfg_value, description) VALUES
('ALERT_TTL_BLOCK_H',    '4',    'BLOCK급(통제, 대피 등) 경보의 유효 시간 (4시간)'),
('ALERT_TTL_WARN_H',     '2',    'WARN급(주의, 경보 등) 경보의 유효 시간 (2시간)'),
('ALERT_TTL_IGNORE_H',   '1',    'IGNORE급(기타 정보) TTL (1시간)')
ON DUPLICATE KEY UPDATE
  cfg_value = VALUES(cfg_value),
  description = VALUES(description);

-- ---------------------------------------------------------------------
-- 4) 경보 필터 정책
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
-- 5) 추천 정렬 기준
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
-- 6) 시스템 동작 주기
-- ---------------------------------------------------------------------
INSERT INTO app_config (cfg_key, cfg_value, description) VALUES
('FETCH_INTERVAL_SEC',          '60',  'NMC 실시간 API 데이터 수집 주기(초)'),
('HLI_CALC_INTERVAL_SEC',       '60',  'HLI 계산 주기(초)'),
('ALERT_SYNC_INTERVAL_SEC',     '180', '재난문자 갱신 주기(초)'),
('RANK_REFRESH_INTERVAL_SEC',   '60',  '사용자 위치 기준 재계산 주기(초)')
ON DUPLICATE KEY UPDATE
  cfg_value = VALUES(cfg_value),
  description = VALUES(description);
