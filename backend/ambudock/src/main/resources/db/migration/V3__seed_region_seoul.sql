-- seed_region_seoul.sql
-- Region 시드: 서울특별시(시도 1건) + 25개 자치구(시군구 레벨)
-- 코드 체계: 시도(11) / 시군구(5자리) → 10자리 법정동코드로 시군구는 'XXXXX00000'
-- 예) 강남구 11680 → 1168000000

SET NAMES utf8mb4;
SET time_zone = '+00:00';

START TRANSACTION;

-- 1) 시도 레벨(서울특별시) 업서트
INSERT INTO region (code, name_full, name_sido, name_sigungu, name_dong, created_at, updated_at)
VALUES
  ('1100000000','서울특별시','서울특별시',NULL,NULL,NOW(3),NOW(3))
ON DUPLICATE KEY UPDATE
  name_full=VALUES(name_full),
  name_sido=VALUES(name_sido),
  name_sigungu=NULL,
  name_dong=NULL,
  updated_at=NOW(3);

-- 2) 시/군/구 레벨(자치구 25개) 업서트
INSERT INTO region (code, name_full, name_sido, name_sigungu, name_dong, created_at, updated_at)
VALUES
  ('1111000000','서울특별시 종로구','서울특별시','종로구',NULL,NOW(3),NOW(3)),
  ('1114000000','서울특별시 중구','서울특별시','중구',NULL,NOW(3),NOW(3)),
  ('1117000000','서울특별시 용산구','서울특별시','용산구',NULL,NOW(3),NOW(3)),
  ('1120000000','서울특별시 성동구','서울특별시','성동구',NULL,NOW(3),NOW(3)),
  ('1121500000','서울특별시 광진구','서울특별시','광진구',NULL,NOW(3),NOW(3)),
  ('1123000000','서울특별시 동대문구','서울특별시','동대문구',NULL,NOW(3),NOW(3)),
  ('1126000000','서울특별시 중랑구','서울특별시','중랑구',NULL,NOW(3),NOW(3)),
  ('1129000000','서울특별시 성북구','서울특별시','성북구',NULL,NOW(3),NOW(3)),
  ('1130500000','서울특별시 강북구','서울특별시','강북구',NULL,NOW(3),NOW(3)),
  ('1132000000','서울특별시 도봉구','서울특별시','도봉구',NULL,NOW(3),NOW(3)),
  ('1135000000','서울특별시 노원구','서울특별시','노원구',NULL,NOW(3),NOW(3)),
  ('1138000000','서울특별시 은평구','서울특별시','은평구',NULL,NOW(3),NOW(3)),
  ('1141000000','서울특별시 서대문구','서울특별시','서대문구',NULL,NOW(3),NOW(3)),
  ('1144000000','서울특별시 마포구','서울특별시','마포구',NULL,NOW(3),NOW(3)),
  ('1147000000','서울특별시 양천구','서울특별시','양천구',NULL,NOW(3),NOW(3)),
  ('1150000000','서울특별시 강서구','서울특별시','강서구',NULL,NOW(3),NOW(3)),
  ('1153000000','서울특별시 구로구','서울특별시','구로구',NULL,NOW(3),NOW(3)),
  ('1154500000','서울특별시 금천구','서울특별시','금천구',NULL,NOW(3),NOW(3)),
  ('1156000000','서울특별시 영등포구','서울특별시','영등포구',NULL,NOW(3),NOW(3)),
  ('1159000000','서울특별시 동작구','서울특별시','동작구',NULL,NOW(3),NOW(3)),
  ('1162000000','서울특별시 관악구','서울특별시','관악구',NULL,NOW(3),NOW(3)),
  ('1165000000','서울특별시 서초구','서울특별시','서초구',NULL,NOW(3),NOW(3)),
  ('1168000000','서울특별시 강남구','서울특별시','강남구',NULL,NOW(3),NOW(3)),
  ('1171000000','서울특별시 송파구','서울특별시','송파구',NULL,NOW(3),NOW(3)),
  ('1174000000','서울특별시 강동구','서울특별시','강동구',NULL,NOW(3),NOW(3))
ON DUPLICATE KEY UPDATE
  name_full=VALUES(name_full),
  name_sido=VALUES(name_sido),
  name_sigungu=VALUES(name_sigungu),
  name_dong=NULL,
  updated_at=NOW(3);

