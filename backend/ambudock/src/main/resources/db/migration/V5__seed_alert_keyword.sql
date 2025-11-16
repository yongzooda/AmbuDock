-- =====================================================================
-- V3__seed_alert_keyword.sql
-- 목적: severity 보정
--  - 물공급/수압 저하, 저수지 방류·수위 상승, 산불/공장 화재,
--    멧돼지 출몰, "유의" 안내 문구 등을 WARN으로 인식하도록 패턴 추가
--  - V2에서 INSERT 한 기존 규칙은 그대로 두고, 여기서는 보강용 규칙만 INSERT
-- =====================================================================

-- -------------------------------------------------
-- WARN: 수자원/상수도/급수 이상 (수압 저하, 물공급 장애 등)
--   예: "수압 저하로 물공급이 원활하지 않은 일부 지역"
-- -------------------------------------------------
INSERT INTO alert_keyword (severity, pattern, enabled, priority, updated_at) VALUES
('WARN', '수압\\s*저하',                                1, 58, UTC_TIMESTAMP(3)),
('WARN', '물공급(이)?\\s*(원활하지|중단|불가)',           1, 58, UTC_TIMESTAMP(3)),
('WARN', '급수(장애|중단|제한)',                         1, 58, UTC_TIMESTAMP(3)),
('WARN', '상수도\\s*(공급\\s*장애|공급\\s*중단|유량\\s*저하)', 1, 58, UTC_TIMESTAMP(3));

-- -------------------------------------------------
-- WARN: 저수지/댐 방류 & 수위 상승
--   예: "저수지 방류로 하천수위가 상승할 수 있으니…"
-- -------------------------------------------------
INSERT INTO alert_keyword (severity, pattern, enabled, priority, updated_at) VALUES
('WARN', '저수지\\s*방류',                             1, 56, UTC_TIMESTAMP(3)),
('WARN', '수위.*상승',                                 1, 56, UTC_TIMESTAMP(3));

-- -------------------------------------------------
-- WARN: 산불/일반 화재 (대형 화재는 V2에서 BLOCK 패턴 유지)
--   예: "산불 발생", "공장 화재 발생", "물류센터 화재 발생"
-- -------------------------------------------------
INSERT INTO alert_keyword (severity, pattern, enabled, priority, updated_at) VALUES
('WARN', '산불(\\s*발생)?',                            1, 55, UTC_TIMESTAMP(3)),
('WARN', '화재\\s*발생',                               1, 55, UTC_TIMESTAMP(3)),
('WARN', '공장\\s*화재',                               1, 55, UTC_TIMESTAMP(3)),
('WARN', '물류센터\\s*화재',                           1, 55, UTC_TIMESTAMP(3));

-- -------------------------------------------------
-- WARN: 야생동물(멧돼지) 출몰
--   예: "멧돼지 출몰 신고가 접수되었으니 … 유의하시기 바랍니다"
-- -------------------------------------------------
INSERT INTO alert_keyword (severity, pattern, enabled, priority, updated_at) VALUES
('WARN', '멧돼지\\s*출몰',                             1, 57, UTC_TIMESTAMP(3));

-- -------------------------------------------------
-- WARN: "유의" 안내 문구 전반
--   예: "안전에 유의하시기 바랍니다", "외출에 유의해 주시기 바랍니다"
--   → 병원 접근성에 어느 정도 영향이 있다고 보고 WARN 처리
-- -------------------------------------------------
INSERT INTO alert_keyword (severity, pattern, enabled, priority, updated_at) VALUES
('WARN', '유의(하시기|하십시오|해\\s*주시기|하여\\s*주시기|바랍니다)?', 1, 57, UTC_TIMESTAMP(3));
