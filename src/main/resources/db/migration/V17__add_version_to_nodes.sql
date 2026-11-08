-- 노드 이름·메모 동시 수정 시 오래된 화면 기준의 요청이 최신 내용을 덮어쓰지 않도록 낙관적 락용 version 컬럼을 추가한다.
--  - JPA @Version 이 수정 시 UPDATE ... SET version = version + 1 WHERE node_id = ? AND version = ? 로 확인과 저장을 한 문장에 처리한다.
--  - 기존 노드는 0 부터 시작한다.
ALTER TABLE nodes
    ADD COLUMN version BIGINT NOT NULL DEFAULT 0;
