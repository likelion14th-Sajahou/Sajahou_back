-- =============================================================
-- 사자록 ERD 최종본 (MySQL 8, ERD 클라우드 가져오기용)
-- 기준: 2026-11-05 백엔드 ERD 3안 결정 + 기능명세서 11/5 수정본 반영 + 연결 테이블 3개 JSON 전환
--       + 기능명세서 11/6 수정본 반영 (가입 정보 입력 추가)
--       + 기능명세서 11/8 최종본 반영 (기록 1개 = 프로젝트 카드 1장, 대표 카드 없앰, 포트폴리오는 프로젝트에 저장)
--
-- 표기 규칙
-- * 테이블 이름은 단수 snake_case. user만 예약어와 겹쳐서 users.
-- * PK는 BIGINT AUTO_INCREMENT. 연결 테이블(2개)은 복합 PK.
-- * 반대 방향 조회·삭제 정합성이 필요 없는 다대다 값은 JSON 컬럼 (project.roles, job_posting.competencies, cover_letter_question.competency_ids).
-- * 바뀌지 않는 고정 값은 ENUM, 늘어날 수 있는 값은 VARCHAR(허용 값은 COMMENT).
-- * FK 제약은 전부 건다. 외래 키가 걸리는 순서대로 테이블을 만든다.
-- * 연결 테이블의 FK만 ON DELETE CASCADE. 업무 데이터(첨부, 질문 등)는 서비스 코드에서 지운다.
-- * AI 질문의 소속 FK는 하나만 채운다. 첨부는 올린 직후 소속이 없고(2단계 업로드), 연결되면 하나만 채운다. 서비스 코드에서 검사한다.
-- =============================================================


-- 회원: 구글 로그인 후 가입 정보 입력 (S-01, S-03~S-05, H-01, R-24). 정보를 모두 받은 뒤에 행을 만든다. 시연용 데모 계정도 이 테이블의 한 행이다.
CREATE TABLE users (
  id                BIGINT       NOT NULL AUTO_INCREMENT COMMENT '회원 ID',
  email             VARCHAR(255) NOT NULL COMMENT '구글 이메일',
  provider_id       VARCHAR(255) NOT NULL COMMENT '구글 고유 ID (로그인 조회용)',
  name              VARCHAR(50)  NOT NULL COMMENT '이름',
  student_no        VARCHAR(20)  NOT NULL COMMENT '학번 (가입 화면에서 8자리 숫자로 검사)',
  department        VARCHAR(50)  NOT NULL COMMENT '학과 (목록에서 고른 이름 또는 기타에 직접 입력한 이름)',
  cohort            INT          NOT NULL COMMENT '기수 (12, 13, 14. 기본 14)',
  part              ENUM('PLAN_DESIGN', 'FRONTEND', 'BACKEND') NOT NULL COMMENT '파트 (가입 후 고정, 바꾸려면 운영진)',
  privacy_agreed_at DATETIME(6)  NOT NULL COMMENT '개인정보 수집·이용 동의 시각 (S-05, 동의 없이는 가입 불가)',
  created_at        DATETIME(6)  NOT NULL DEFAULT CURRENT_TIMESTAMP(6) COMMENT '생성일',
  updated_at        DATETIME(6)  NOT NULL DEFAULT CURRENT_TIMESTAMP(6) COMMENT '수정일',
  PRIMARY KEY (id),
  UNIQUE KEY uk_users_email (email),
  UNIQUE KEY uk_users_provider_id (provider_id)
) COMMENT = '회원';


-- 39주 일정, 운영 데이터 (C-05, A-02~A-04)
-- 구간: 정기 세션 1(1~9), 아이디어톤(10~13), 정기 세션 2(14~20), 해커톤(21~27), 정기 세션 3(28~34), 중커톤(35~38), 마무리(39)
CREATE TABLE week (
  id                   BIGINT      NOT NULL AUTO_INCREMENT COMMENT '주차 ID',
  week_number          INT         NOT NULL COMMENT '주차 번호 (1~39)',
  phase                ENUM('REGULAR_1', 'IDEATHON', 'REGULAR_2', 'HACKATHON', 'REGULAR_3', 'JUNGKATHON', 'WRAP_UP') NOT NULL COMMENT '구간 (명세 A-02)',
  start_date           DATE        NOT NULL COMMENT '주차 시작일 (월요일)',
  is_presentation_week BOOLEAN     NOT NULL DEFAULT FALSE COMMENT '프로젝트 발표 주 여부 (해님 모양)',
  created_at           DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) COMMENT '생성일',
  updated_at           DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) COMMENT '수정일',
  PRIMARY KEY (id),
  UNIQUE KEY uk_week_week_number (week_number)
) COMMENT = '39주 일정 (운영 데이터)';


-- 주차·파트별 세션과 마감, 운영 데이터 (C-05, R-07, R-15, R-24, A-06)
CREATE TABLE week_part_schedule (
  id                  BIGINT       NOT NULL AUTO_INCREMENT COMMENT '일정 ID',
  week_id             BIGINT       NOT NULL COMMENT '주차 ID',
  part                ENUM('PLAN_DESIGN', 'FRONTEND', 'BACKEND') NOT NULL COMMENT '파트',
  session_title       VARCHAR(100) NULL     COMMENT '세션 제목',
  topic_summary       TEXT         NULL     COMMENT '세션 내용 요약 (AI 프롬프트에 들어감)',
  session_deadline    DATETIME(6)  NOT NULL COMMENT '세션 회고 마감',
  assignment_deadline DATETIME(6)  NULL     COMMENT '과제 회고 마감 (과제 없으면 NULL)',
  created_at          DATETIME(6)  NOT NULL DEFAULT CURRENT_TIMESTAMP(6) COMMENT '생성일',
  updated_at          DATETIME(6)  NOT NULL DEFAULT CURRENT_TIMESTAMP(6) COMMENT '수정일',
  PRIMARY KEY (id),
  UNIQUE KEY uk_wps_week_part (week_id, part),
  CONSTRAINT fk_wps_week FOREIGN KEY (week_id) REFERENCES week (id)
) COMMENT = '주차·파트별 세션과 마감 (운영 데이터)';


-- 파트별 고정 역량 목록, 운영 데이터 (C-06, R-20, A-07, M-06)
-- 공통 7개 + 파트별 8개씩 = 31행. 회원은 공통 + 내 파트 = 15개만 쓴다.
-- '코드 품질'·'개념 이해'는 FE와 BE에 뜻이 다르게 들어 있어 (part, name)으로 구분한다.
CREATE TABLE competency (
  id          BIGINT       NOT NULL AUTO_INCREMENT COMMENT '역량 ID',
  part        ENUM('COMMON', 'PLAN_DESIGN', 'FRONTEND', 'BACKEND') NOT NULL COMMENT '공통 또는 파트',
  name        VARCHAR(50)  NOT NULL COMMENT '역량 이름',
  description VARCHAR(200) NOT NULL COMMENT '역량 뜻 한 줄 (칩 툴팁, AI 프롬프트)',
  sort_order  INT          NOT NULL DEFAULT 0 COMMENT '표시 순서',
  created_at  DATETIME(6)  NOT NULL DEFAULT CURRENT_TIMESTAMP(6) COMMENT '생성일',
  updated_at  DATETIME(6)  NOT NULL DEFAULT CURRENT_TIMESTAMP(6) COMMENT '수정일',
  PRIMARY KEY (id),
  UNIQUE KEY uk_competency_part_name (part, name)
) COMMENT = '파트별 고정 역량 목록 (운영 데이터)';


-- 프로젝트 (H-08~H-11, H-18, H-21, H-23~H-25). 역할은 roles JSON 배열. 포트폴리오는 프로젝트당 하나만 저장한다.
CREATE TABLE project (
  id                        BIGINT       NOT NULL AUTO_INCREMENT COMMENT '프로젝트 ID',
  user_id                   BIGINT       NOT NULL COMMENT '회원 ID',
  name                      VARCHAR(100) NOT NULL COMMENT '프로젝트 이름',
  subtitle                  VARCHAR(100) NULL     COMMENT '부제',
  roles                     JSON         NOT NULL COMMENT '내 역할 배열, 여러 개: ["PM", "PLANNING", "DESIGN", "FRONTEND", "BACKEND"] 중 (H-10)',
  status                    ENUM('IN_PROGRESS', 'DONE') NOT NULL DEFAULT 'IN_PROGRESS' COMMENT '상태 (진행 중, 완료). 완료해도 잠기지 않음',
  portfolio_markdown        MEDIUMTEXT   NULL     COMMENT '저장한 포트폴리오 Markdown. 없으면 NULL (H-18, H-21)',
  portfolio_saved_at        DATETIME(6)  NULL     COMMENT '포트폴리오를 저장한 시각',
  created_at                DATETIME(6)  NOT NULL DEFAULT CURRENT_TIMESTAMP(6) COMMENT '시작일 (만든 날)',
  updated_at                DATETIME(6)  NOT NULL DEFAULT CURRENT_TIMESTAMP(6) COMMENT '수정일',
  PRIMARY KEY (id),
  CONSTRAINT fk_project_user FOREIGN KEY (user_id) REFERENCES users (id)
) COMMENT = '프로젝트';


-- 프로젝트 기록 (H-12~H-15, H-21, H-22). 저장(SAVED)한 기록 1개 = 프로젝트 카드 1장.
-- 'AI와 정리하기'는 질문을 기록에 붙여야 해서 DRAFT로 먼저 만들고, 역량 태그와 함께 저장하면 SAVED가 되면서 카드가 생긴다.
CREATE TABLE project_record (
  id              BIGINT      NOT NULL AUTO_INCREMENT COMMENT '기록 ID',
  project_id      BIGINT      NOT NULL COMMENT '프로젝트 ID',
  status          ENUM('DRAFT', 'SAVED') NOT NULL DEFAULT 'DRAFT' COMMENT 'AI 정리 중(DRAFT, 목록에 안 보임) / 저장됨(SAVED, 카드 있음)',
  memo            TEXT        NULL     COMMENT '짧은 메모',
  did             TEXT        NULL     COMMENT '한 일',
  concern         TEXT        NULL     COMMENT '고민·해결',
  decision        TEXT        NULL     COMMENT '선택과 이유',
  result          TEXT        NULL     COMMENT '결과·반응',
  next_step       TEXT        NULL     COMMENT '다음',
  is_ai_organized BOOLEAN     NOT NULL DEFAULT FALSE COMMENT 'AI와 정리 여부',
  created_at      DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) COMMENT '기록 날짜',
  updated_at      DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) COMMENT '수정일',
  PRIMARY KEY (id),
  KEY idx_record_project_status (project_id, status, created_at),
  CONSTRAINT fk_record_project FOREIGN KEY (project_id) REFERENCES project (id)
) COMMENT = '프로젝트 기록';


-- 회고: 세션·과제를 type으로 구분 (R-01~R-23, H-03)
-- 아직 시작하지 않은 주는 행이 없다. 시작 전에 휴회를 누르면 is_rested = TRUE로 행을 만든다.
-- 노션용 회고록은 스토리 카드의 full_markdown에 저장한다.
CREATE TABLE retrospective (
  id                  BIGINT      NOT NULL AUTO_INCREMENT COMMENT '회고 ID',
  user_id             BIGINT      NOT NULL COMMENT '회원 ID',
  week_id             BIGINT      NOT NULL COMMENT '주차 ID',
  type                ENUM('SESSION', 'ASSIGNMENT') NOT NULL COMMENT '회고 유형',
  status              ENUM('IN_PROGRESS', 'COMPLETED') NOT NULL DEFAULT 'IN_PROGRESS' COMMENT '상태',
  is_rested           BOOLEAN     NOT NULL DEFAULT FALSE COMMENT '휴회 여부 (세션 회고만)',
  current_step        INT         NULL     COMMENT '이어 쓰기 재개 단계 (2~4)',
  has_practice        BOOLEAN     NULL     COMMENT '실습 여부 (고르기 전 NULL)',
  practice_content    TEXT        NULL     COMMENT '실습 내용',
  assignment_mode     ENUM('RECORD', 'LATER', 'NONE') NULL COMMENT '과제 작성 방식 (세션 회고만)',
  assignment_content  MEDIUMTEXT  NULL     COMMENT '과제 내용, 코드, 에러',
  code_review         TEXT        NULL     COMMENT '받은 코드리뷰 (FE만)',
  memo                TEXT        NULL     COMMENT '고민 메모',
  question_input_hash CHAR(64)    NULL     COMMENT '질문을 만들 때의 자료 상태 해시 (R-14, 자료가 바뀌었는지 판단)',
  last_saved_at       DATETIME(6) NULL     COMMENT '마지막 임시저장 시각 (저장됨 표시)',
  completed_at        DATETIME(6) NULL     COMMENT '완료 시각 (마감보다 늦으면 지각)',
  created_at          DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) COMMENT '생성일',
  updated_at          DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) COMMENT '수정일',
  PRIMARY KEY (id),
  UNIQUE KEY uk_retro_user_week_type (user_id, week_id, type),
  CONSTRAINT fk_retro_user FOREIGN KEY (user_id) REFERENCES users (id),
  CONSTRAINT fk_retro_week FOREIGN KEY (week_id) REFERENCES week (id)
) COMMENT = '회고';


-- 채용 공고 = 자소서 매칭 한 건 (M-02, M-03, M-05)
CREATE TABLE job_posting (
  id                      BIGINT       NOT NULL AUTO_INCREMENT COMMENT '공고 ID',
  user_id                 BIGINT       NOT NULL COMMENT '회원 ID',
  company                 VARCHAR(100) NULL     COMMENT '회사',
  department              VARCHAR(100) NULL     COMMENT '채용 부서',
  posting_text            TEXT         NULL     COMMENT '붙여 넣은 공고 본문',
  jd                      TEXT         NULL     COMMENT '주요 업무·자격 요건',
  preferred_qualification TEXT         NULL     COMMENT '우대사항',
  company_info            TEXT         NULL     COMMENT '기업 정보·인재상',
  competencies            JSON         NULL     COMMENT '공고가 원하는 역량 (M-06): [{"competencyId": 3, "evidenceQuote": "공고 속 근거 문장"}]',
  created_at              DATETIME(6)  NOT NULL DEFAULT CURRENT_TIMESTAMP(6) COMMENT '생성일',
  updated_at              DATETIME(6)  NOT NULL DEFAULT CURRENT_TIMESTAMP(6) COMMENT '수정일',
  PRIMARY KEY (id),
  CONSTRAINT fk_posting_user FOREIGN KEY (user_id) REFERENCES users (id)
) COMMENT = '채용 공고 (자소서 매칭 한 건)';


-- 첨부 파일·링크: 회고, 프로젝트 기록, 프로젝트(산출물), 공고 중 한 곳에 속함 (R-08, R-11, H-12, H-17, M-02)
-- 2단계 업로드: 먼저 올리면 소속 FK가 모두 NULL이고, 저장할 때 하나가 채워진다. 24시간 동안 연결되지 않으면 스케줄러가 지운다.
CREATE TABLE attachment (
  id                BIGINT        NOT NULL AUTO_INCREMENT COMMENT '첨부 ID',
  user_id           BIGINT        NOT NULL COMMENT '올린 회원 ID (연결 전 소유 확인, 미연결 정리)',
  retrospective_id  BIGINT        NULL     COMMENT '회고 ID',
  project_record_id BIGINT        NULL     COMMENT '프로젝트 기록 ID',
  project_id        BIGINT        NULL     COMMENT '프로젝트 ID (마치기 산출물)',
  job_posting_id    BIGINT        NULL     COMMENT '공고 ID (공고 파일·링크)',
  type              ENUM('FILE', 'LINK') NOT NULL COMMENT '파일/링크',
  category          VARCHAR(30)   NOT NULL COMMENT '구분: SESSION_PDF, PRACTICE, ASSIGNMENT, MEETING_NOTE, PRESENTATION, DESIGN, OUTPUT, JOB_POSTING',
  name              VARCHAR(255)  NULL     COMMENT '원래 파일명',
  s3_key            VARCHAR(500)  NULL     COMMENT 'S3 위치 (파일일 때)',
  url               VARCHAR(2048) NULL     COMMENT '링크 주소 (링크일 때)',
  link_type         VARCHAR(30)   NULL     COMMENT '링크 종류: GITHUB, FIGMA, NOTION, OTHER',
  content_type      VARCHAR(100)  NULL     COMMENT 'MIME 타입',
  size_bytes        BIGINT        NULL     COMMENT '파일 크기',
  extracted_text    LONGTEXT      NULL     COMMENT '추출한 텍스트 (AI 입력, 근거 검증)',
  extract_status    ENUM('PENDING', 'DONE', 'FAILED', 'SKIPPED') NOT NULL DEFAULT 'PENDING' COMMENT '추출 상태 (링크: 본문 읽음 = DONE, 읽지 못함 = FAILED)',
  created_at        DATETIME(6)   NOT NULL DEFAULT CURRENT_TIMESTAMP(6) COMMENT '생성일',
  updated_at        DATETIME(6)   NOT NULL DEFAULT CURRENT_TIMESTAMP(6) COMMENT '수정일',
  PRIMARY KEY (id),
  KEY idx_attach_user_created (user_id, created_at),
  CONSTRAINT fk_attach_user    FOREIGN KEY (user_id)           REFERENCES users (id),
  CONSTRAINT fk_attach_retro   FOREIGN KEY (retrospective_id)  REFERENCES retrospective (id),
  CONSTRAINT fk_attach_record  FOREIGN KEY (project_record_id) REFERENCES project_record (id),
  CONSTRAINT fk_attach_project FOREIGN KEY (project_id)        REFERENCES project (id),
  CONSTRAINT fk_attach_posting FOREIGN KEY (job_posting_id)    REFERENCES job_posting (id)
) COMMENT = '첨부 파일·링크';


-- AI 질문과 답: 회고, 프로젝트 기록, 프로젝트(마치기) 중 한 곳에 속함 (R-14~R-18, H-13, H-17)
-- '질문 새로 만들기'를 고르면 기존 질문을 지우고 새로 넣는다 (덮어쓰기).
CREATE TABLE ai_question (
  id                   BIGINT      NOT NULL AUTO_INCREMENT COMMENT '질문 ID',
  retrospective_id     BIGINT      NULL     COMMENT '회고 ID',
  project_record_id    BIGINT      NULL     COMMENT '프로젝트 기록 ID',
  project_id           BIGINT      NULL     COMMENT '프로젝트 ID (마치기 2단계)',
  seq                  INT         NOT NULL COMMENT '질문 순서',
  tag                  VARCHAR(50) NOT NULL COMMENT '질문 성격 태그: 근거와 숫자, 빠진 칸, 세션과 연결, 역량 신호, 느낀 점 등',
  quote                TEXT        NULL     COMMENT '근거 인용 문장 (원문 대조 통과한 것만)',
  source_attachment_id BIGINT      NULL     COMMENT '인용한 첨부 ID (첨부가 지워지면 NULL)',
  content              TEXT        NOT NULL COMMENT '질문',
  answer               TEXT        NULL     COMMENT '답변',
  status               ENUM('PENDING', 'ANSWERED', 'SKIPPED') NOT NULL DEFAULT 'PENDING' COMMENT '상태 (모르겠어요는 건너뛰기로 통일)',
  created_at           DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) COMMENT '생성일',
  updated_at           DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) COMMENT '수정일',
  PRIMARY KEY (id),
  CONSTRAINT fk_aiq_retro   FOREIGN KEY (retrospective_id)     REFERENCES retrospective (id),
  CONSTRAINT fk_aiq_record  FOREIGN KEY (project_record_id)    REFERENCES project_record (id),
  CONSTRAINT fk_aiq_project FOREIGN KEY (project_id)           REFERENCES project (id),
  CONSTRAINT fk_aiq_source  FOREIGN KEY (source_attachment_id) REFERENCES attachment (id) ON DELETE SET NULL
) COMMENT = 'AI 질문과 답';


-- 스토리 카드: 회고 하나에 한 장, 저장한 프로젝트 기록 하나에 한 장 (R-20, R-21, A-06~A-10, H-14, H-15, H-22, M-08)
-- 프로젝트 카드는 기록에서 만든다: 제목 = 한 일, 상황 = 고민·해결, 행동 = 한 일, 이유 = 선택과 이유, 결과 = 결과·반응.
CREATE TABLE story_card (
  id                   BIGINT       NOT NULL AUTO_INCREMENT COMMENT '카드 ID',
  user_id              BIGINT       NOT NULL COMMENT '회원 ID',
  type                 ENUM('SESSION', 'ASSIGNMENT', 'PROJECT') NOT NULL COMMENT '출처 유형',
  retrospective_id     BIGINT       NULL     COMMENT '출처 회고 ID (회고 하나에 카드 한 장)',
  project_id           BIGINT       NULL     COMMENT '출처 프로젝트 ID (프로젝트 카드, 조회용으로 기록의 프로젝트와 같게 저장)',
  project_record_id    BIGINT       NULL     COMMENT '출처 프로젝트 기록 ID (기록 하나에 카드 한 장)',
  week_id              BIGINT       NULL     COMMENT '주차 ID (필터·정렬용, 프로젝트 카드는 NULL)',
  status               ENUM('DRAFT', 'PUBLISHED') NOT NULL DEFAULT 'DRAFT' COMMENT '초안/사자록에 공개',
  title                VARCHAR(200) NULL     COMMENT '제목',
  situation            TEXT         NULL     COMMENT '상황 (기록에 없으면 NULL, 화면에 ⚑)',
  action               TEXT         NULL     COMMENT '행동',
  reason               TEXT         NULL     COMMENT '이유',
  result               TEXT         NULL     COMMENT '결과',
  learned              TEXT         NULL     COMMENT '배운 점',
  full_markdown        MEDIUMTEXT   NULL     COMMENT '전문: 회고 카드는 노션용 회고록, 프로젝트 카드는 기록 전문 Markdown',
  evidence             JSON         NULL     COMMENT '근거 (칸별 출처 첨부 ID와 인용 문장)',
  created_at           DATETIME(6)  NOT NULL DEFAULT CURRENT_TIMESTAMP(6) COMMENT '생성일',
  updated_at           DATETIME(6)  NOT NULL DEFAULT CURRENT_TIMESTAMP(6) COMMENT '수정일',
  PRIMARY KEY (id),
  UNIQUE KEY uk_card_retro (retrospective_id),
  UNIQUE KEY uk_card_record (project_record_id),
  CONSTRAINT fk_card_user    FOREIGN KEY (user_id)          REFERENCES users (id),
  CONSTRAINT fk_card_retro   FOREIGN KEY (retrospective_id) REFERENCES retrospective (id),
  CONSTRAINT fk_card_project FOREIGN KEY (project_id)       REFERENCES project (id),
  CONSTRAINT fk_card_record  FOREIGN KEY (project_record_id) REFERENCES project_record (id),
  CONSTRAINT fk_card_week    FOREIGN KEY (week_id)          REFERENCES week (id)
) COMMENT = '스토리 카드';


-- 카드-역량 연결 (N:M). 역량은 사용자가 고른다(AI가 고르지 않음). 회고 카드는 초안일 때 0개도 되고 공개할 때 1~3개, 프로젝트 카드는 기록을 저장할 때 1~3개 (R-20, H-14)
CREATE TABLE card_competency (
  card_id       BIGINT NOT NULL COMMENT '카드 ID',
  competency_id BIGINT NOT NULL COMMENT '역량 ID',
  PRIMARY KEY (card_id, competency_id),
  CONSTRAINT fk_cc_card       FOREIGN KEY (card_id)       REFERENCES story_card (id) ON DELETE CASCADE,
  CONSTRAINT fk_cc_competency FOREIGN KEY (competency_id) REFERENCES competency (id)
) COMMENT = '카드-역량 연결';


-- 자소서 문항과 답 (M-04, M-08~M-11, M-14)
CREATE TABLE cover_letter_question (
  id             BIGINT      NOT NULL AUTO_INCREMENT COMMENT '문항 ID',
  job_posting_id BIGINT      NOT NULL COMMENT '공고 ID',
  seq            INT         NOT NULL COMMENT '문항 순서',
  content        TEXT        NOT NULL COMMENT '문항',
  char_limit     INT         NULL     COMMENT '글자 수 제한 (P1, 없으면 NULL)',
  competency_ids JSON        NULL     COMMENT '문항이 묻는 역량 ID 배열 (M-08 추천, M-13 없는 역량): [3, 7]',
  answer_draft   JSON        NULL     COMMENT 'AI 답 초안 (문장·빈칸 조각 배열과 출처 번호)',
  edited_answer  TEXT        NULL     COMMENT '직접 고친 답 (NULL이면 초안 그대로)',
  created_at     DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) COMMENT '생성일',
  updated_at     DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) COMMENT '수정일',
  PRIMARY KEY (id),
  UNIQUE KEY uk_clq_posting_seq (job_posting_id, seq),
  CONSTRAINT fk_clq_posting FOREIGN KEY (job_posting_id) REFERENCES job_posting (id)
) COMMENT = '자소서 문항과 답';


-- 답에 쓴 카드: ① 추천 카드, ② 보조 카드 (M-08, M-09, M-12). ③ 지원 공고는 job_posting 자체.
-- 카드를 지우면 이 연결도 함께 지워진다 (A-10: 지운 카드는 자소서 매칭에 쓰이지 않음).
CREATE TABLE cover_letter_question_card (
  cover_letter_question_id BIGINT NOT NULL COMMENT '문항 ID',
  card_id                  BIGINT NOT NULL COMMENT '카드 ID',
  role                     ENUM('MAIN', 'SUB') NOT NULL COMMENT '① 추천 카드(MAIN), ② 보조 카드(SUB)',
  PRIMARY KEY (cover_letter_question_id, card_id),
  CONSTRAINT fk_clqcard_question FOREIGN KEY (cover_letter_question_id) REFERENCES cover_letter_question (id) ON DELETE CASCADE,
  CONSTRAINT fk_clqcard_card     FOREIGN KEY (card_id)                  REFERENCES story_card (id)            ON DELETE CASCADE
) COMMENT = '답에 쓴 카드';
