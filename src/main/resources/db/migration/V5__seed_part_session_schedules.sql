-- 기준: Notion seed 확정표. 날짜와 마감 일시는 KST 기준이다.
-- 모든 주(휴회 포함)의 수요일 이후 처음 돌아오는 해당 요일 23:59가 마감이다.
ALTER TABLE week_part_schedule
    ADD COLUMN session_date DATE NULL AFTER session_title;

CREATE TEMPORARY TABLE seed_part_session_schedule (
    week_number INT NOT NULL,
    part VARCHAR(20) NOT NULL,
    session_title VARCHAR(100) NOT NULL,
    session_date DATE NOT NULL,
    PRIMARY KEY (week_number, part)
) DEFAULT CHARACTER SET utf8mb4;

INSERT INTO seed_part_session_schedule (week_number, part, session_title, session_date)
VALUES
    (1, 'PLAN_DESIGN', 'OT · 공통 세션', '2026-03-04'),
    (1, 'BACKEND', '공통 세션', '2026-03-04'),
    (1, 'FRONTEND', '공통 세션', '2026-03-04'),
    (2, 'PLAN_DESIGN', '문제정의 / UI·UX 입문', '2026-03-11'),
    (2, 'BACKEND', 'Git & Github', '2026-03-11'),
    (2, 'FRONTEND', 'Git & Github', '2026-03-11'),
    (3, 'PLAN_DESIGN', '가설수립·가설검증 / Figma 입문', '2026-03-18'),
    (3, 'BACKEND', 'Django 시작하기', '2026-03-18'),
    (3, 'FRONTEND', 'HTML / CSS (1)', '2026-03-18'),
    (4, 'PLAN_DESIGN', '인터뷰 / 정렬·그리드·오토레이아웃', '2026-03-25'),
    (4, 'BACKEND', 'DB', '2026-03-25'),
    (4, 'FRONTEND', 'HTML / CSS (2)', '2026-03-25'),
    (5, 'PLAN_DESIGN', '기능도출·기능명세서', '2026-04-01'),
    (5, 'BACKEND', 'CRUD', '2026-04-01'),
    (5, 'FRONTEND', 'JS (1) : 변수, 기본 자료형, 함수, DOM', '2026-04-01'),
    (6, 'PLAN_DESIGN', '기획산출물 / 와이어프레임', '2026-04-08'),
    (6, 'BACKEND', 'AWS EC2', '2026-04-08'),
    (6, 'FRONTEND', 'JS (2) : object, class', '2026-04-08'),
    (7, 'PLAN_DESIGN', '노션 / 스타일가이드', '2026-04-15'),
    (7, 'BACKEND', '중간고사 휴회', '2026-04-15'),
    (7, 'FRONTEND', '중간고사 휴회', '2026-04-15'),
    (8, 'PLAN_DESIGN', 'BM과 IR', '2026-04-22'),
    (8, 'BACKEND', '중간고사 휴회', '2026-04-22'),
    (8, 'FRONTEND', '중간고사 휴회', '2026-04-22'),
    (9, 'PLAN_DESIGN', '아이디어톤 팀빌딩', '2026-04-29'),
    (9, 'BACKEND', 'DRF API', '2026-04-29'),
    (9, 'FRONTEND', 'JS (3) : data fetching', '2026-04-29'),
    (10, 'PLAN_DESIGN', '아이디어톤 · 문제 정의', '2026-05-06'),
    (10, 'BACKEND', 'JWT', '2026-05-06'),
    (10, 'FRONTEND', 'React (1): Virtual DOM, JSX', '2026-05-06'),
    (11, 'PLAN_DESIGN', '아이디어톤 · 사용자 인터뷰', '2026-05-13'),
    (11, 'BACKEND', '아이디어톤 팀 시간', '2026-05-13'),
    (11, 'FRONTEND', '아이디어톤 팀 시간', '2026-05-13'),
    (12, 'PLAN_DESIGN', '아이디어톤 · 프로토타입', '2026-05-20'),
    (12, 'BACKEND', '아이디어톤 회고', '2026-05-20'),
    (12, 'FRONTEND', '아이디어톤 회고', '2026-05-20'),
    (13, 'PLAN_DESIGN', '아이디어톤 발표·회고', '2026-05-27'),
    (13, 'BACKEND', 'OAUTH', '2026-05-27'),
    (13, 'FRONTEND', 'React (2): useState, props', '2026-05-27'),
    (14, 'PLAN_DESIGN', '아이디어톤 기디 회고', '2026-06-03'),
    (14, 'BACKEND', '기말고사 휴회', '2026-06-03'),
    (14, 'FRONTEND', '기말고사 휴회', '2026-06-03'),
    (15, 'PLAN_DESIGN', '기능명세서 심화', '2026-06-10'),
    (15, 'BACKEND', '기말고사 휴회', '2026-06-10'),
    (15, 'FRONTEND', '기말고사 휴회', '2026-06-10'),
    (16, 'PLAN_DESIGN', '컴포넌트 기초', '2026-06-17'),
    (16, 'BACKEND', '기말고사 휴회', '2026-06-17'),
    (16, 'FRONTEND', '기말고사 휴회', '2026-06-17'),
    (17, 'PLAN_DESIGN', '기획자의 커뮤니케이션', '2026-06-24'),
    (17, 'BACKEND', 'IAM & RDS & S3 & 스웨거', '2026-06-24'),
    (17, 'FRONTEND', 'React (3): Routing, Lifecycle, useEffectd', '2026-06-24'),
    (18, 'PLAN_DESIGN', '기디 데모데이', '2026-07-01'),
    (18, 'BACKEND', 'HTTPS', '2026-07-01'),
    (18, 'FRONTEND', 'React (4): context api, recoil', '2026-07-01'),
    (19, 'PLAN_DESIGN', 'PM/DE를 위한 AI 활용법', '2026-07-08'),
    (19, 'BACKEND', '예외처리', '2026-07-08'),
    (19, 'FRONTEND', 'React (5): Axios, 인증, 인가, 로컬스토리지', '2026-07-08'),
    (20, 'PLAN_DESIGN', 'Slack·GitHub / 프로토타이핑', '2026-07-15'),
    (20, 'BACKEND', 'SpringBoot 시작하기', '2026-07-15'),
    (20, 'FRONTEND', '비밀 세션', '2026-07-15'),
    (21, 'PLAN_DESIGN', '해커톤 팀빌딩', '2026-07-22'),
    (21, 'BACKEND', '해커톤 팀빌딩', '2026-07-22'),
    (21, 'FRONTEND', '해커톤 팀빌딩', '2026-07-22'),
    (22, 'PLAN_DESIGN', '해커톤 · 아이디어 확정', '2026-07-29'),
    (22, 'BACKEND', 'JPA :작동 원리', '2026-07-29'),
    (22, 'FRONTEND', 'Vite + 배포 / Tailwind CSS', '2026-07-29'),
    (23, 'PLAN_DESIGN', '해커톤 · 기능 명세', '2026-08-05'),
    (23, 'BACKEND', 'Spring Data JPA', '2026-08-05'),
    (23, 'FRONTEND', 'Zustand', '2026-08-05'),
    (24, 'PLAN_DESIGN', '해커톤 · 디자인', '2026-08-12'),
    (24, 'BACKEND', '해커톤 팀 시간 (1)', '2026-08-12'),
    (24, 'FRONTEND', '해커톤 팀 시간 (1)', '2026-08-12'),
    (25, 'PLAN_DESIGN', '해커톤 · 개발 협업', '2026-08-19'),
    (25, 'BACKEND', '해커톤 팀 시간 (2)', '2026-08-19'),
    (25, 'FRONTEND', '해커톤 팀 시간 (2)', '2026-08-19'),
    (26, 'PLAN_DESIGN', '해커톤 · 중간 점검', '2026-08-26'),
    (26, 'BACKEND', '해커톤 · 중간 점검', '2026-08-26'),
    (26, 'FRONTEND', '해커톤 · 중간 점검', '2026-08-26'),
    (27, 'PLAN_DESIGN', '해커톤 발표·회고', '2026-09-02'),
    (27, 'BACKEND', '전체 회고', '2026-09-02'),
    (27, 'FRONTEND', '전체 회고', '2026-09-02'),
    (28, 'PLAN_DESIGN', '선배와의 만남', '2026-09-09'),
    (28, 'BACKEND', '선배와의 만남', '2026-09-09'),
    (28, 'FRONTEND', '선배와의 만남', '2026-09-09'),
    (29, 'PLAN_DESIGN', '역기획&리디자인 1', '2026-09-16'),
    (29, 'BACKEND', '비밀 세션', '2026-09-16'),
    (29, 'FRONTEND', 'TypeScript', '2026-09-16'),
    (30, 'PLAN_DESIGN', '역기획&리디자인 2', '2026-09-23'),
    (30, 'BACKEND', 'CRUD', '2026-09-23'),
    (30, 'FRONTEND', 'TanStack Query', '2026-09-23'),
    (31, 'PLAN_DESIGN', '기획자의 문서화', '2026-09-30'),
    (31, 'BACKEND', 'Spring Security', '2026-09-30'),
    (31, 'FRONTEND', 'Next.js (1)', '2026-09-30'),
    (32, 'PLAN_DESIGN', '기디 특별 세션', '2026-10-07'),
    (32, 'BACKEND', 'SpringBoot OAuth 2.0', '2026-10-07'),
    (32, 'FRONTEND', 'Next.js (2)', '2026-10-07'),
    (33, 'PLAN_DESIGN', '포트폴리오 세션', '2026-10-14'),
    (33, 'BACKEND', '포트폴리오 세션', '2026-10-14'),
    (33, 'FRONTEND', '포트폴리오 세션', '2026-10-14'),
    (34, 'PLAN_DESIGN', '중간고사 휴회', '2026-10-21'),
    (34, 'BACKEND', '중간고사 휴회', '2026-10-21'),
    (34, 'FRONTEND', '중간고사 휴회', '2026-10-21'),
    (35, 'PLAN_DESIGN', '역기획&리디자인 3', '2026-10-28'),
    (35, 'BACKEND', 'JWT', '2026-10-28'),
    (35, 'FRONTEND', 'AI API', '2026-10-28'),
    (36, 'PLAN_DESIGN', '역기획&리디자인 4', '2026-11-04'),
    (36, 'BACKEND', '배포', '2026-11-04'),
    (36, 'FRONTEND', '최적화', '2026-11-04'),
    (37, 'PLAN_DESIGN', '기디 마지막 세션', '2026-11-11'),
    (37, 'BACKEND', '테스트코드', '2026-11-11'),
    (37, 'FRONTEND', 'CI/CD & GitHub Actions', '2026-11-11'),
    (38, 'PLAN_DESIGN', '중커톤 최종 발표', '2026-11-18'),
    (38, 'BACKEND', '중커톤 최종 발표', '2026-11-18'),
    (38, 'FRONTEND', '중커톤 최종 발표', '2026-11-18'),
    (39, 'PLAN_DESIGN', '마무리 · 수료', '2026-11-25'),
    (39, 'BACKEND', '14기의 밤', '2026-11-25'),
    (39, 'FRONTEND', '14기의 밤', '2026-11-25');

ALTER TABLE seed_part_session_schedule
    ADD COLUMN session_deadline DATETIME(6),
    ADD COLUMN assignment_deadline DATETIME(6);

UPDATE seed_part_session_schedule
SET session_deadline = TIMESTAMP(
        DATE_ADD(session_date, INTERVAL (CASE WHEN part = 'FRONTEND' THEN 6 ELSE 4 END) DAY),
        '23:59:00'),
    assignment_deadline = TIMESTAMP(
        DATE_ADD(session_date, INTERVAL (CASE part
            WHEN 'PLAN_DESIGN' THEN 6
            WHEN 'FRONTEND' THEN 5
            ELSE 4
        END) DAY),
        '23:59:00');

UPDATE week_part_schedule s
JOIN week w ON w.id = s.week_id
JOIN seed_part_session_schedule seed ON seed.week_number = w.week_number AND seed.part = s.part
SET s.session_title = seed.session_title,
    s.session_date = seed.session_date,
    s.session_deadline = seed.session_deadline,
    s.assignment_deadline = seed.assignment_deadline;

INSERT INTO week_part_schedule
    (week_id, part, session_title, session_date, session_deadline, assignment_deadline)
SELECT w.id, seed.part, seed.session_title, seed.session_date,
       seed.session_deadline, seed.assignment_deadline
FROM seed_part_session_schedule seed
JOIN week w ON w.week_number = seed.week_number
LEFT JOIN week_part_schedule s ON s.week_id = w.id AND s.part = seed.part
WHERE s.id IS NULL;

DROP TEMPORARY TABLE seed_part_session_schedule;
