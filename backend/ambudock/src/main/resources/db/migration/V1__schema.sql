-- =====================================================================
-- V1__schema_utc.sql
-- AmbuDock Database Schema (MySQL 8.0)
-- =====================================================================

-- 세션 설정 (마이그레이션이 실행되는 세션에서 UTC로 고정)
SET NAMES utf8mb4;
SET time_zone = '+00:00';

-- ---------------------------------------------------------------------
-- 1) Region (행정구역 마스터)
-- ---------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS region (
  region_id    BIGINT       NOT NULL AUTO_INCREMENT,
  code         VARCHAR(20)  NOT NULL,
  name_full    VARCHAR(255) NOT NULL,
  name_sido    VARCHAR(100) NOT NULL,
  name_sigungu VARCHAR(100) NULL,
  name_dong    VARCHAR(100) NULL,
  created_at   DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  updated_at   DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
  CONSTRAINT PK_region_id PRIMARY KEY (region_id),
  CONSTRAINT UK_region_code UNIQUE (code)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- ---------------------------------------------------------------------
-- 2) Hospital (병원 마스터)
-- ---------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS hospital (
  hpid          VARCHAR(20)  NOT NULL,
  name          VARCHAR(255) NOT NULL,
  lat           DECIMAL(11,8) NULL,
  lon           DECIMAL(11,8) NULL,
  addr_road     VARCHAR(255) NULL,
  addr_jibun    VARCHAR(255) NULL,
  region_id     BIGINT       NULL,
  tel_emergency VARCHAR(50)  NULL,
  tel_main      VARCHAR(50)  NULL,
  created_at    DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  updated_at    DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
  CONSTRAINT PK_hospital PRIMARY KEY (hpid),
  KEY FK_hospital_region_id (region_id),
  CONSTRAINT FK_hospital_region
    FOREIGN KEY (region_id) REFERENCES region (region_id)
    ON UPDATE CASCADE ON DELETE SET NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- ---------------------------------------------------------------------
-- 3) HospitalStatus (NMC 실시간 스냅샷)
-- ---------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS hospital_status (
  status_id   BIGINT       NOT NULL AUTO_INCREMENT,
  hpid        VARCHAR(20)  NOT NULL,
  hvidate     DATETIME(3)  NOT NULL,            -- NMC 갱신시각(원천, 반드시 UTC로 변환해 저장)
  ingested_at DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3), -- 수집시각(서버, UTC)
  hvec        INT          NOT NULL DEFAULT 0,  -- ER 가용 병상
  hv2         INT          NULL DEFAULT 0,
  hv3         INT          NULL DEFAULT 0,
  hv4         INT          NULL DEFAULT 0,
  hv5         INT          NULL DEFAULT 0,
  hv6         INT          DEFAULT 0,
  hvoc        INT          DEFAULT 0,           -- 수술실 가용
  CONSTRAINT PK_hospital_status PRIMARY KEY (status_id),
  KEY FK_hospital_status_hpid (hpid),
  KEY IDX_hospital_status_hpid_hvidate (hpid, hvidate),
  CONSTRAINT FK_hospital_status_hospital
    FOREIGN KEY (hpid) REFERENCES hospital (hpid)
    ON UPDATE CASCADE ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- ---------------------------------------------------------------------
-- 4) HliSnapshot (혼잡지수 계산 결과)
-- ---------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS hli_snapshot (
  hli_id        BIGINT        NOT NULL AUTO_INCREMENT,
  status_id     BIGINT        NOT NULL,
  hpid          VARCHAR(20)   NOT NULL,               -- 조인 최적화용 중복 컬럼
  calc_at       DATETIME(3)   NOT NULL DEFAULT CURRENT_TIMESTAMP(3), -- 계산 시각(서버 UTC)
  er_base_score TINYINT       NOT NULL DEFAULT 0,
  icu_avail     INT           NOT NULL DEFAULT 0,     -- hv2+hv3+hv4+hv5+hv6+hvoc
  icu_bucket    ENUM('NONE','CRITICAL_LOW','LOW','MID','HIGH') NOT NULL DEFAULT 'NONE',
  icu_adjust    SMALLINT      NOT NULL DEFAULT 0,
  lag_sec       INT           NOT NULL DEFAULT 0,
  stale_level   ENUM('FRESH','SOFT','HARD') NOT NULL DEFAULT 'FRESH',
  score         TINYINT       NOT NULL DEFAULT 0,     -- 0~100 clamp
  level         ENUM('GREEN','YELLOW','RED','GRAY') NOT NULL DEFAULT 'RED',
  reason        JSON          NULL,
  CONSTRAINT PK_hli_snapshot PRIMARY KEY (hli_id),
  CONSTRAINT UK_hli_status UNIQUE (status_id),
  KEY FK_hli_hpid (hpid),
  CONSTRAINT FK_hli_status
    FOREIGN KEY (status_id) REFERENCES hospital_status (status_id)
    ON UPDATE CASCADE ON DELETE CASCADE,
  CONSTRAINT FK_hli_hospital
    FOREIGN KEY (hpid) REFERENCES hospital (hpid)
    ON UPDATE CASCADE ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- ---------------------------------------------------------------------
-- 5) DisasterAlert (재난 문자)
-- ---------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS disaster_alert (
  alert_id      BIGINT        NOT NULL AUTO_INCREMENT,
  md101_sn      VARCHAR(40)   NOT NULL,  -- API 고유 식별자
  issued_at     DATETIME(3)   NOT NULL,  -- 원문 발행시각(원천), 반드시 UTC로 변환해 저장
  location_id   VARCHAR(40)   NOT NULL,
  location_name VARCHAR(120)  NOT NULL,
  msg           TEXT          NOT NULL,
  severity      ENUM('IGNORE','WARN','BLOCK') NOT NULL DEFAULT 'IGNORE',
  expires_at    DATETIME(3)   NULL,      -- 만료(UTC)
  is_active     TINYINT(1)    NOT NULL DEFAULT 1,
  CONSTRAINT PK_disaster_alert PRIMARY KEY (alert_id),
  CONSTRAINT UK_disaster_alert_md101_sn UNIQUE (md101_sn)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- ---------------------------------------------------------------------
-- 6) AlertKeyword (키워드 룰)
-- ---------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS alert_keyword (
  rule_id    BIGINT        NOT NULL AUTO_INCREMENT,
  severity   ENUM('IGNORE','WARN','BLOCK') NOT NULL,
  pattern    VARCHAR(300)  NOT NULL,   -- 정규식 패턴
  enabled    TINYINT(1)    NOT NULL DEFAULT 1,
  priority   INT           NOT NULL DEFAULT 100,
  updated_at DATETIME(3)   NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
  CONSTRAINT PK_alert_keyword PRIMARY KEY (rule_id),
  KEY IDX_alert_keyword_enabled_priority (enabled, priority)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- ---------------------------------------------------------------------
-- 7) AlertRegionLink (재난경보 ↔ 지역 매핑)
-- ---------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS alert_region_link (
  alert_region_id BIGINT      NOT NULL AUTO_INCREMENT,
  alert_id        BIGINT      NOT NULL,
  region_id       BIGINT      NOT NULL,
  match_level     ENUM('SIDO','SIGUNGU','DONG') NULL,
  created_at      DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  updated_at      DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
  CONSTRAINT PK_alert_region_link PRIMARY KEY (alert_region_id),
  KEY FK_alert_region_link_alert_id (alert_id),
  KEY FK_alert_region_link_region_id (region_id),
  CONSTRAINT UK_alert_region UNIQUE (alert_id, region_id),
  CONSTRAINT FK_alert_region_link_alert
    FOREIGN KEY (alert_id) REFERENCES disaster_alert (alert_id)
    ON UPDATE CASCADE ON DELETE CASCADE,
  CONSTRAINT FK_alert_region_link_region
    FOREIGN KEY (region_id) REFERENCES region (region_id)
    ON UPDATE CASCADE ON DELETE RESTRICT
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- ---------------------------------------------------------------------
-- 8) AppConfig (운영 파라미터)
-- ---------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS app_config (
  cfg_key     VARCHAR(60)  NOT NULL,
  cfg_value   VARCHAR(120) NOT NULL,
  description VARCHAR(200) NULL,
  updated_at  DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
  CONSTRAINT PK_app_config PRIMARY KEY (cfg_key)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- ---------------------------------------------------------------------
-- 9) RankingEvent (추천/순위 계산 로그)
-- ---------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS ranking_event (
  event_id        BIGINT        NOT NULL AUTO_INCREMENT,
  calc_at         DATETIME(3)   NOT NULL DEFAULT CURRENT_TIMESTAMP(3), -- 계산 시각(UTC)
  origin_lat      DECIMAL(10,7) NOT NULL,
  origin_lon      DECIMAL(10,7) NOT NULL,
  candidate_count INT           NOT NULL DEFAULT 0,
  result_json     JSON          NOT NULL,
  duration_ms     INT           NOT NULL DEFAULT 0,
  CONSTRAINT PK_ranking_event PRIMARY KEY (event_id),
  KEY IDX_ranking_event_calc_at (calc_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
