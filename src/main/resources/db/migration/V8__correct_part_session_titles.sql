-- 확정 일정표에 맞춰 파트별 세션 제목을 정정한다.
-- 세션 날짜는 V5와 일치한다.
UPDATE week_part_schedule s
JOIN week w ON w.id = s.week_id
JOIN (
    SELECT 1 AS week_number, 'PLAN_DESIGN' AS part, '공통 세션' AS session_title
    UNION ALL SELECT 5, 'PLAN_DESIGN', '기능도출·기능명세서 / 오토레이아웃 심화'
    UNION ALL SELECT 7, 'PLAN_DESIGN', '중간고사 휴회'
    UNION ALL SELECT 8, 'PLAN_DESIGN', '중간고사 휴회'
    UNION ALL SELECT 9, 'PLAN_DESIGN', '노션 / 스타일가이드'
    UNION ALL SELECT 10, 'PLAN_DESIGN', 'BM과 IR'
    UNION ALL SELECT 11, 'PLAN_DESIGN', '아이디어톤 팀 시간'
    UNION ALL SELECT 12, 'PLAN_DESIGN', '아이디어톤 회고'
    UNION ALL SELECT 13, 'PLAN_DESIGN', '아이디어톤 기디 회고'
    UNION ALL SELECT 13, 'BACKEND', 'OAUTH'
    UNION ALL SELECT 13, 'FRONTEND', 'React (2): useState, props'
    UNION ALL SELECT 14, 'PLAN_DESIGN', '기말고사 휴회'
    UNION ALL SELECT 15, 'PLAN_DESIGN', '기말고사 휴회'
    UNION ALL SELECT 16, 'PLAN_DESIGN', '기말고사 휴회'
    UNION ALL SELECT 17, 'PLAN_DESIGN', '기능명세서 심화 / 리디자인 발표'
    UNION ALL SELECT 18, 'PLAN_DESIGN', '컴포넌트 기초'
    UNION ALL SELECT 19, 'PLAN_DESIGN', '커뮤니케이션을 위해 기획자가 알아야 할 개발 지식'
    UNION ALL SELECT 20, 'PLAN_DESIGN', '기디 데모데이'
    UNION ALL SELECT 22, 'PLAN_DESIGN', 'PM/DE를 위한 AI 활용법'
    UNION ALL SELECT 23, 'PLAN_DESIGN', 'Slack·GitHub / 프로토타이핑'
    UNION ALL SELECT 27, 'PLAN_DESIGN', '전체 회고'
    UNION ALL SELECT 28, 'PLAN_DESIGN', '선배와의 만남'
    UNION ALL SELECT 28, 'BACKEND', '선배와의 만남'
    UNION ALL SELECT 28, 'FRONTEND', '선배와의 만남'
    UNION ALL SELECT 29, 'PLAN_DESIGN', '휴회'
    UNION ALL SELECT 30, 'PLAN_DESIGN', '역기획&리디자인 1'
    UNION ALL SELECT 31, 'PLAN_DESIGN', '역기획&리디자인 2'
    UNION ALL SELECT 32, 'PLAN_DESIGN', '역기획&리디자인 3'
    UNION ALL SELECT 33, 'PLAN_DESIGN', '중간고사 휴회'
    UNION ALL SELECT 33, 'BACKEND', '중간고사 휴회'
    UNION ALL SELECT 33, 'FRONTEND', '중간고사 휴회'
    UNION ALL SELECT 35, 'PLAN_DESIGN', '중커톤 협업 가이드 / 역기획&리디자인 4 (기디 Demo-Day)'
    UNION ALL SELECT 36, 'PLAN_DESIGN', '기디 특별 세션'
    UNION ALL SELECT 37, 'PLAN_DESIGN', '기디 마지막 세션 (한 해를 돌아보며)'
    UNION ALL SELECT 38, 'PLAN_DESIGN', '중커톤 최종 발표'
    UNION ALL SELECT 38, 'BACKEND', '중커톤 최종 발표'
    UNION ALL SELECT 38, 'FRONTEND', '중커톤 최종 발표'
    UNION ALL SELECT 39, 'PLAN_DESIGN', '14기의 밤'
) AS schedule ON schedule.week_number = w.week_number AND schedule.part = s.part
SET s.session_title = schedule.session_title
WHERE NOT (BINARY s.session_title <=> BINARY schedule.session_title);
