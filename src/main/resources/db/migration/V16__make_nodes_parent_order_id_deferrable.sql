-- 형제 노드 순서 재정렬 시 order_id 를 한꺼번에 밀고 당기면 중간 상태에서 (parent_id, order_id) 가 잠깐 겹친다.
-- partial unique index 는 DEFERRABLE 로 만들 수 없으므로 같은 이름의 UNIQUE 제약으로 교체한다.
--  - parent_id 가 NULL 인 루트는 UNIQUE 제약에서 NULL 끼리 서로 다르게 취급되므로 기존 WHERE parent_id IS NOT NULL 과 의미가 같다.
--  - INITIALLY IMMEDIATE 라 평소에는 기존과 동일하게 즉시 검사하고,
--    재정렬 트랜잭션에서만 SET CONSTRAINTS uk_nodes_parent_order_id DEFERRED 로 커밋 시점까지 검사를 미룬다.
--  - 제약 이름을 유지해 ConstraintErrorCodeMapper 의 DUPLICATED_ORDER_ID 매핑이 그대로 동작한다.
DROP INDEX uk_nodes_parent_order_id;

ALTER TABLE nodes
    ADD CONSTRAINT uk_nodes_parent_order_id
    UNIQUE (parent_id, order_id)
    DEFERRABLE INITIALLY IMMEDIATE;
