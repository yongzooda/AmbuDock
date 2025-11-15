-- =====================================================================
-- seed_alert_keyword.sql
-- 목적: 재난문자 심각도 판정용 AlertKeyword 초기 시드
-- 규칙:
--   - enabled = 1 인 것만 평가 대상
--   - priority: 낮을수록 먼저 평가 (예: IGNORE 1x, BLOCK 1x~3x, WARN 5x~9x)
--   - 자바 측 룰 엔진은 IGNORE 매칭 시 즉시 반환(무효화)하도록 구현되어 있음
-- 주의:
--   - 정규식은 Java Pattern 기준 (SQL에서 저장만 함)
--   - 일부 공백 허용: \\s* 사용
-- =====================================================================

-- -------------------------------------------------
-- IGNORE (해제/종료/정상화/훈련/테스트 등) — 우선순위 가장 높게(작은 수)
-- -------------------------------------------------
INSERT INTO alert_keyword (severity, pattern, enabled, priority, updated_at) VALUES
('IGNORE', '해제',                               1, 5,  UTC_TIMESTAMP(3)),
('IGNORE', '종료',                               1, 5,  UTC_TIMESTAMP(3)),
('IGNORE', '진화완료',                           1, 6,  UTC_TIMESTAMP(3)),
('IGNORE', '복구완료',                           1, 6,  UTC_TIMESTAMP(3)),
('IGNORE', '정상화',                             1, 6,  UTC_TIMESTAMP(3)),
('IGNORE', '대피\\s*해제',                        1, 7,  UTC_TIMESTAMP(3)),
('IGNORE', '(주의보|경보)\\s*해제',               1, 7,  UTC_TIMESTAMP(3)),
('IGNORE', '통제\\s*해제',                        1, 7,  UTC_TIMESTAMP(3)),
('IGNORE', '통행\\s*재개',                        1, 8,  UTC_TIMESTAMP(3)),
('IGNORE', '상황\\s*종료',                        1, 8,  UTC_TIMESTAMP(3)),
('IGNORE', '훈련',                               1, 9,  UTC_TIMESTAMP(3)),
('IGNORE', '모의훈련',                           1, 9,  UTC_TIMESTAMP(3)),
('IGNORE', '훈련문자',                           1, 9,  UTC_TIMESTAMP(3)),
('IGNORE', '훈련\\s*안내',                        1, 9,  UTC_TIMESTAMP(3)),
('IGNORE', '테스트',                             1, 9,  UTC_TIMESTAMP(3)),
('IGNORE', '시험\\s*발송',                        1, 10, UTC_TIMESTAMP(3)),
('IGNORE', '점검\\s*문자',                        1, 10, UTC_TIMESTAMP(3)),
('IGNORE', '시범\\s*운영',                        1, 10, UTC_TIMESTAMP(3)),
('IGNORE', '오발송',                             1, 10, UTC_TIMESTAMP(3));

-- -------------------------------------------------
-- BLOCK (즉시 대피/전면 통제/출입 통제/통행불가/침수/범람/붕괴/폭발/화학물질 누출 등 강한 위험)
-- ※ 일부 통제(부분/일부 통제)는 WARN에 따로 두어 과도 차단 방지.
-- ※ 너무 포괄적인 "통제" 단독 키워드는 피하고 강한 패턴만 수록.
-- -------------------------------------------------
INSERT INTO alert_keyword (severity, pattern, enabled, priority, updated_at) VALUES
('BLOCK', '즉시\\s*대피',                        1, 12, UTC_TIMESTAMP(3)),
('BLOCK', '긴급\\s*대피',                        1, 12, UTC_TIMESTAMP(3)),
('BLOCK', '대피(하십시오|하세요|요망|바랍니다|요청|명령|령)', 1, 13, UTC_TIMESTAMP(3)),
('BLOCK', '피난(하십시오|하세요)?',               1, 13, UTC_TIMESTAMP(3)),

('BLOCK', '전면\\s*통제',                        1, 14, UTC_TIMESTAMP(3)),
('BLOCK', '전면통제',                             1, 14, UTC_TIMESTAMP(3)),
('BLOCK', '전면\\s*차량\\s*통제',                 1, 14, UTC_TIMESTAMP(3)),
('BLOCK', '출입\\s*통제',                         1, 15, UTC_TIMESTAMP(3)),
('BLOCK', '접근\\s*금지',                         1, 15, UTC_TIMESTAMP(3)),

('BLOCK', '통행\\s*불가',                         1, 16, UTC_TIMESTAMP(3)),
('BLOCK', '도로\\s*유실',                         1, 16, UTC_TIMESTAMP(3)),
('BLOCK', '교량\\s*(통제|붕괴)',                  1, 16, UTC_TIMESTAMP(3)),
('BLOCK', '산사태\\s*발생',                       1, 17, UTC_TIMESTAMP(3)),
('BLOCK', '토사\\s*유출',                         1, 17, UTC_TIMESTAMP(3)),

('BLOCK', '침수(중|로)?',                         1, 18, UTC_TIMESTAMP(3)),
('BLOCK', '급격한\\s*침수',                        1, 18, UTC_TIMESTAMP(3)),
('BLOCK', '범람(중|경보)?',                        1, 18, UTC_TIMESTAMP(3)),
('BLOCK', '댐\\s*붕괴',                           1, 18, UTC_TIMESTAMP(3)),

('BLOCK', '붕괴',                                1, 19, UTC_TIMESTAMP(3)),
('BLOCK', '폭발',                                1, 19, UTC_TIMESTAMP(3)),
('BLOCK', '대형\\s*화재',                         1, 19, UTC_TIMESTAMP(3)),
('BLOCK', '유해(화학|화학물질)\\s*누출',           1, 20, UTC_TIMESTAMP(3)),
('BLOCK', '가스\\s*누출',                          1, 20, UTC_TIMESTAMP(3)),

('BLOCK', '지진(\\s*발생|\\s*감지)?',               1, 21, UTC_TIMESTAMP(3)),
('BLOCK', '해일\\s*경보',                          1, 21, UTC_TIMESTAMP(3)),
('BLOCK', '쓰나미',                                1, 21, UTC_TIMESTAMP(3)),

('BLOCK', '대피소로\\s*이동',                      1, 22, UTC_TIMESTAMP(3)),
('BLOCK', '(위험|위기)\\s*지역\\s*진입\\s*금지',     1, 22, UTC_TIMESTAMP(3));

-- -------------------------------------------------
-- WARN (주의/경보/기상·환경·교통 위험, 부분 통제, 침수/범람 우려 등)
-- -------------------------------------------------
INSERT INTO alert_keyword (severity, pattern, enabled, priority, updated_at) VALUES
-- 기상/재해 전반
('WARN',  '주의(하세요|바랍니다)?',               1, 50, UTC_TIMESTAMP(3)),
('WARN',  '주의보',                               1, 50, UTC_TIMESTAMP(3)),
('WARN',  '경계',                                 1, 51, UTC_TIMESTAMP(3)),
('WARN',  '경보( 발령)?',                          1, 52, UTC_TIMESTAMP(3)),

('WARN',  '호우|폭우|강풍|태풍|풍랑',               1, 53, UTC_TIMESTAMP(3)),
('WARN',  '대설|폭설|한파|폭염|건조',               1, 53, UTC_TIMESTAMP(3)),
('WARN',  '황사|미세먼지',                         1, 54, UTC_TIMESTAMP(3)),
('WARN',  '낙뢰|우박',                             1, 54, UTC_TIMESTAMP(3)),
('WARN',  '해일\\s*주의보',                        1, 55, UTC_TIMESTAMP(3)),
('WARN',  '산불\\s*주의',                          1, 55, UTC_TIMESTAMP(3)),
('WARN',  '산사태\\s*주의',                        1, 55, UTC_TIMESTAMP(3)),

-- 수문/침수 위험
('WARN',  '침수\\s*우려',                          1, 56, UTC_TIMESTAMP(3)),
('WARN',  '범람\\s*우려',                          1, 56, UTC_TIMESTAMP(3)),
('WARN',  '수위\\s*상승',                          1, 56, UTC_TIMESTAMP(3)),
('WARN',  '댐\\s*방류',                            1, 56, UTC_TIMESTAMP(3)),

-- 도로/교통 위험
('WARN',  '결빙|빙판',                             1, 57, UTC_TIMESTAMP(3)),
('WARN',  '서행|감속운행',                          1, 57, UTC_TIMESTAMP(3)),
('WARN',  '빗길',                                 1, 57, UTC_TIMESTAMP(3)),

-- 사회 기반 위험(광역 영향)
('WARN',  '정전',                                 1, 58, UTC_TIMESTAMP(3)),
('WARN',  '대규모\\s*정전',                        1, 58, UTC_TIMESTAMP(3)),
('WARN',  '단수',                                 1, 58, UTC_TIMESTAMP(3)),
('WARN',  '상수도\\s*유량\\s*저하',                  1, 58, UTC_TIMESTAMP(3)),

-- 통제(부분/일부) — BLOCK 키워드보다 먼저 잡아 완화 효과 유도(텍스트상 중복시 서버 로직상 BLOCK이 우선되므로, BLOCK에 "전면/출입" 등 강한 것만 남겼음)
('WARN',  '부분\\s*통제',                           1, 60, UTC_TIMESTAMP(3)),
('WARN',  '일부\\s*통제',                           1, 60, UTC_TIMESTAMP(3)),
('WARN',  '부분통제|일부구간\\s*통제',               1, 60, UTC_TIMESTAMP(3)),

-- 기타 안전 권고
('WARN',  '안전\\s*운행|안전\\s*확인',                1, 61, UTC_TIMESTAMP(3)),
('WARN',  '대피\\s*준비',                            1, 61, UTC_TIMESTAMP(3)),
('WARN',  '인근\\s*하천\\s*접근\\s*주의',              1, 61, UTC_TIMESTAMP(3)),
('WARN',  '공사구간\\s*주의',                         1, 61, UTC_TIMESTAMP(3));


