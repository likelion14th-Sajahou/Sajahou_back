-- HTML 시안 TL_W, TL_CH, tlDate 기준.
-- session_deadline은 시안에 없어 세션 당일 23:59 KST로 임시 설정한다.
-- 시안의 기획·디자인 일정만 등록한다. 과제 마감은 NULL로 둔다.
INSERT INTO week (week_number, phase, start_date, is_presentation_week)
VALUES
    (1, 'REGULAR_1', '2026-03-02', FALSE),
    (2, 'REGULAR_1', '2026-03-09', FALSE),
    (3, 'REGULAR_1', '2026-03-16', FALSE),
    (4, 'REGULAR_1', '2026-03-23', FALSE),
    (5, 'REGULAR_1', '2026-03-30', FALSE),
    (6, 'REGULAR_1', '2026-04-06', FALSE),
    (7, 'REGULAR_1', '2026-04-13', FALSE),
    (8, 'REGULAR_1', '2026-04-20', FALSE),
    (9, 'REGULAR_1', '2026-04-27', FALSE),
    (10, 'IDEATHON', '2026-05-04', FALSE),
    (11, 'IDEATHON', '2026-05-11', FALSE),
    (12, 'IDEATHON', '2026-05-18', FALSE),
    (13, 'IDEATHON', '2026-05-25', TRUE),
    (14, 'REGULAR_2', '2026-06-01', FALSE),
    (15, 'REGULAR_2', '2026-06-08', FALSE),
    (16, 'REGULAR_2', '2026-06-15', FALSE),
    (17, 'REGULAR_2', '2026-06-22', FALSE),
    (18, 'REGULAR_2', '2026-06-29', FALSE),
    (19, 'REGULAR_2', '2026-07-06', FALSE),
    (20, 'REGULAR_2', '2026-07-13', FALSE),
    (21, 'HACKATHON', '2026-07-20', FALSE),
    (22, 'HACKATHON', '2026-07-27', FALSE),
    (23, 'HACKATHON', '2026-08-03', FALSE),
    (24, 'HACKATHON', '2026-08-10', FALSE),
    (25, 'HACKATHON', '2026-08-17', FALSE),
    (26, 'HACKATHON', '2026-08-24', FALSE),
    (27, 'HACKATHON', '2026-08-31', TRUE),
    (28, 'REGULAR_3', '2026-09-07', FALSE),
    (29, 'REGULAR_3', '2026-09-14', FALSE),
    (30, 'REGULAR_3', '2026-09-21', FALSE),
    (31, 'REGULAR_3', '2026-09-28', FALSE),
    (32, 'REGULAR_3', '2026-10-05', FALSE),
    (33, 'REGULAR_3', '2026-10-12', FALSE),
    (34, 'REGULAR_3', '2026-10-19', FALSE),
    (35, 'JUNGKATHON', '2026-10-26', FALSE),
    (36, 'JUNGKATHON', '2026-11-02', FALSE),
    (37, 'JUNGKATHON', '2026-11-09', FALSE),
    (38, 'JUNGKATHON', '2026-11-16', TRUE),
    (39, 'WRAP_UP', '2026-11-23', FALSE);

INSERT INTO week_part_schedule (week_id, part, session_title, session_deadline, assignment_deadline)
SELECT w.id, 'PLAN_DESIGN', seed.session_title, seed.session_deadline, NULL
FROM (
         SELECT 1 AS week_number, 'OT · 공통 세션' AS session_title, CAST('2026-03-04 23:59:00' AS DATETIME) AS session_deadline
         UNION ALL
         SELECT 2 AS week_number, '문제정의 / UI·UX 입문' AS session_title, CAST('2026-03-11 23:59:00' AS DATETIME) AS session_deadline
         UNION ALL
         SELECT 3 AS week_number, '가설수립·가설검증 / Figma 입문' AS session_title, CAST('2026-03-18 23:59:00' AS DATETIME) AS session_deadline
         UNION ALL
         SELECT 4 AS week_number, '인터뷰 / 정렬·그리드·오토레이아웃' AS session_title, CAST('2026-03-25 23:59:00' AS DATETIME) AS session_deadline
         UNION ALL
         SELECT 5 AS week_number, '기능도출·기능명세서' AS session_title, CAST('2026-04-01 23:59:00' AS DATETIME) AS session_deadline
         UNION ALL
         SELECT 6 AS week_number, '기획산출물 / 와이어프레임' AS session_title, CAST('2026-04-08 23:59:00' AS DATETIME) AS session_deadline
         UNION ALL
         SELECT 7 AS week_number, '노션 / 스타일가이드' AS session_title, CAST('2026-04-15 23:59:00' AS DATETIME) AS session_deadline
         UNION ALL
         SELECT 8 AS week_number, 'BM과 IR' AS session_title, CAST('2026-04-22 23:59:00' AS DATETIME) AS session_deadline
         UNION ALL
         SELECT 9 AS week_number, '아이디어톤 팀빌딩' AS session_title, CAST('2026-04-29 23:59:00' AS DATETIME) AS session_deadline
         UNION ALL
         SELECT 10 AS week_number, '아이디어톤 · 문제 정의' AS session_title, CAST('2026-05-06 23:59:00' AS DATETIME) AS session_deadline
         UNION ALL
         SELECT 11 AS week_number, '아이디어톤 · 사용자 인터뷰' AS session_title, CAST('2026-05-13 23:59:00' AS DATETIME) AS session_deadline
         UNION ALL
         SELECT 12 AS week_number, '아이디어톤 · 프로토타입' AS session_title, CAST('2026-05-20 23:59:00' AS DATETIME) AS session_deadline
         UNION ALL
         SELECT 13 AS week_number, '아이디어톤 발표·회고' AS session_title, CAST('2026-05-27 23:59:00' AS DATETIME) AS session_deadline
         UNION ALL
         SELECT 14 AS week_number, '아이디어톤 기디 회고' AS session_title, CAST('2026-06-03 23:59:00' AS DATETIME) AS session_deadline
         UNION ALL
         SELECT 15 AS week_number, '기능명세서 심화' AS session_title, CAST('2026-06-10 23:59:00' AS DATETIME) AS session_deadline
         UNION ALL
         SELECT 16 AS week_number, '컴포넌트 기초' AS session_title, CAST('2026-06-17 23:59:00' AS DATETIME) AS session_deadline
         UNION ALL
         SELECT 17 AS week_number, '기획자의 커뮤니케이션' AS session_title, CAST('2026-06-24 23:59:00' AS DATETIME) AS session_deadline
         UNION ALL
         SELECT 18 AS week_number, '기디 데모데이' AS session_title, CAST('2026-07-01 23:59:00' AS DATETIME) AS session_deadline
         UNION ALL
         SELECT 19 AS week_number, 'PM/DE를 위한 AI 활용법' AS session_title, CAST('2026-07-08 23:59:00' AS DATETIME) AS session_deadline
         UNION ALL
         SELECT 20 AS week_number, 'Slack·GitHub / 프로토타이핑' AS session_title, CAST('2026-07-15 23:59:00' AS DATETIME) AS session_deadline
         UNION ALL
         SELECT 21 AS week_number, '해커톤 팀빌딩' AS session_title, CAST('2026-07-22 23:59:00' AS DATETIME) AS session_deadline
         UNION ALL
         SELECT 22 AS week_number, '해커톤 · 아이디어 확정' AS session_title, CAST('2026-07-29 23:59:00' AS DATETIME) AS session_deadline
         UNION ALL
         SELECT 23 AS week_number, '해커톤 · 기능 명세' AS session_title, CAST('2026-08-05 23:59:00' AS DATETIME) AS session_deadline
         UNION ALL
         SELECT 24 AS week_number, '해커톤 · 디자인' AS session_title, CAST('2026-08-12 23:59:00' AS DATETIME) AS session_deadline
         UNION ALL
         SELECT 25 AS week_number, '해커톤 · 개발 협업' AS session_title, CAST('2026-08-19 23:59:00' AS DATETIME) AS session_deadline
         UNION ALL
         SELECT 26 AS week_number, '해커톤 · 중간 점검' AS session_title, CAST('2026-08-26 23:59:00' AS DATETIME) AS session_deadline
         UNION ALL
         SELECT 27 AS week_number, '해커톤 발표·회고' AS session_title, CAST('2026-09-02 23:59:00' AS DATETIME) AS session_deadline
         UNION ALL
         SELECT 28 AS week_number, '선배와의 만남' AS session_title, CAST('2026-09-09 23:59:00' AS DATETIME) AS session_deadline
         UNION ALL
         SELECT 29 AS week_number, '역기획&리디자인 1' AS session_title, CAST('2026-09-16 23:59:00' AS DATETIME) AS session_deadline
         UNION ALL
         SELECT 30 AS week_number, '역기획&리디자인 2' AS session_title, CAST('2026-09-23 23:59:00' AS DATETIME) AS session_deadline
         UNION ALL
         SELECT 31 AS week_number, '기획자의 문서화' AS session_title, CAST('2026-09-30 23:59:00' AS DATETIME) AS session_deadline
         UNION ALL
         SELECT 32 AS week_number, '기디 특별 세션' AS session_title, CAST('2026-10-07 23:59:00' AS DATETIME) AS session_deadline
         UNION ALL
         SELECT 33 AS week_number, '포트폴리오 세션' AS session_title, CAST('2026-10-14 23:59:00' AS DATETIME) AS session_deadline
         UNION ALL
         SELECT 34 AS week_number, '중간고사 휴회' AS session_title, CAST('2026-10-21 23:59:00' AS DATETIME) AS session_deadline
         UNION ALL
         SELECT 35 AS week_number, '역기획&리디자인 3' AS session_title, CAST('2026-10-28 23:59:00' AS DATETIME) AS session_deadline
         UNION ALL
         SELECT 36 AS week_number, '역기획&리디자인 4' AS session_title, CAST('2026-11-04 23:59:00' AS DATETIME) AS session_deadline
         UNION ALL
         SELECT 37 AS week_number, '기디 마지막 세션' AS session_title, CAST('2026-11-11 23:59:00' AS DATETIME) AS session_deadline
         UNION ALL
         SELECT 38 AS week_number, '중커톤 최종 발표' AS session_title, CAST('2026-11-18 23:59:00' AS DATETIME) AS session_deadline
         UNION ALL
         SELECT 39 AS week_number, '마무리 · 수료' AS session_title, CAST('2026-11-25 23:59:00' AS DATETIME) AS session_deadline
     ) AS seed
         JOIN week w ON w.week_number = seed.week_number;
