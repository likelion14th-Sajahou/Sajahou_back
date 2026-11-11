-- 30번 기록 AI 질문도 회고(17번)처럼 자료가 바뀌면 확인창을 띄운다 (PM 결정 11/11).
-- 질문을 만들 때의 자료 상태를 저장해 두고 다시 부를 때 비교한다. retrospective.question_input_hash와 같은 모양.
ALTER TABLE project_record
  ADD COLUMN question_input_hash CHAR(64) NULL COMMENT '질문을 만들 때의 자료 상태 해시 (H-13, 자료가 바뀌었는지 판단)'
  AFTER is_ai_organized;
