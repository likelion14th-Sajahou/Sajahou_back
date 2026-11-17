# 사자록 · Sajahou Backend

멋쟁이사자처럼 활동에서 쌓은 경험을 회고하고, 스토리 카드로 모아 포트폴리오와 자기소개서 작성에 활용하는 서비스의 백엔드입니다.

- 서비스: https://sajarock.vercel.app

## 주요 기능

| 기능 | 설명 |
| --- | --- |
| 홈 | 이번 주 회고 상태, 스티커, 최근 회고와 프로젝트 조회 |
| 주간 회고 | 회고 작성·임시 저장, 자료 기반 AI 질문, 회고록과 스토리 카드 초안 생성 |
| 프로젝트 | 프로젝트·활동 기록 관리, AI 질문과 기록 정리, 포트폴리오 초안 생성 |
| 나의 사자록 | 주차별 발자국, 스토리 카드 조회·수정·삭제 |
| 자소서 매칭 | 채용 공고 역량 분석, 스토리 카드 추천과 문항별 답변 초안 생성 |
| 첨부 | 파일·링크 업로드와 글자 추출 |
| 계정 | Google 로그인·회원가입, 프로필 수정, 회원 탈퇴, 데모 계정 |

## 기술 스택

| 구분 | 기술 |
| --- | --- |
| 언어 | Java 17 |
| 프레임워크 | Spring Boot 4.1 |
| 데이터 | Spring Data JPA, MySQL 8, Flyway |
| 보안 | Spring Security, Google 로그인 |
| 외부 서비스 | OpenAI API, AWS S3 |
| 배포 | GitHub Actions, AWS EC2 |

## 로컬 실행

```bash
docker compose up -d   # 로컬 MySQL
./gradlew bootRun
```

DB 스키마는 `src/main/resources/db/migration`의 Flyway 마이그레이션으로 만들어집니다.

## 환경 변수

| 이름 | 설명 |
| --- | --- |
| `DB_URL`, `DB_USERNAME`, `DB_PASSWORD` | MySQL 접속 정보 (기본값: 로컬 docker compose) |
| `OPENAI_API_KEY`, `OPENAI_MODEL` | OpenAI 키와 모델 |
| `GOOGLE_CLIENT_ID` | Google 로그인 클라이언트 ID |

## 프로젝트 구조

```
src/main/java/com/sajarok
├── domain
│   ├── user            # 회원·로그인
│   ├── week            # 주차·세션 일정
│   ├── retrospective   # 주간 회고
│   ├── project         # 프로젝트·기록·포트폴리오
│   ├── storycard       # 스토리 카드
│   ├── matching        # 채용 공고·자기소개서 답변 초안
│   ├── attachment      # 첨부 파일·링크
│   ├── question        # AI 질문
│   ├── competency      # 역량 태그
│   ├── footprint       # 사자 발자국
│   └── home            # 홈
├── infra               # AI 호출, 파일 저장소
└── global              # 공통 응답·예외·설정
```

## 협업 규칙

- 작업 브랜치에서 `main`을 대상으로 PR을 작성하고, `main`에 병합되면 배포됩니다.
- 브랜치명: `feat/내용`, `fix/내용` (이슈가 있으면 `fix/#이슈번호-내용`)
- 커밋 메시지: `태그: 내용` 형식, 태그는 `feat`, `fix`, `refactor`, `docs`, `chore`
