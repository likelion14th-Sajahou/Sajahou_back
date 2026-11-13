-- 프로젝트 행사 종류 (11/13 결정). 25번에서 아이디어톤·해커톤·중커톤 중 하나를 꼭 고른다.
-- 완료(포트폴리오 저장)한 프로젝트는 발자국에서 그 행사의 발표 주(13·27·38주차)에 스티커가 붙는다.
-- 지금까지 만든 프로젝트는 모두 이번 중커톤 기간에 만든 것이라 JUNGKATHON으로 채운다.
-- 기본값은 남겨 둔다(앱은 항상 값을 넣고, SQL로 직접 넣는 테스트가 깨지지 않게).
ALTER TABLE project
  ADD COLUMN category ENUM('IDEATHON', 'HACKATHON', 'JUNGKATHON') NOT NULL DEFAULT 'JUNGKATHON'
    COMMENT '행사 종류 (아이디어톤·해커톤·중커톤). 완료하면 발자국 발표 주에 스티커'
  AFTER subtitle;
