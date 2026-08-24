-- 트리-워크스페이스 1:1 매핑을 위한 FK 컬럼 추가
-- 워크스페이스가 삭제되면 연결된 트리도 함께 삭제됩니다 (ON DELETE CASCADE).

-- 1. 우선 nullable로 컬럼 추가 (기존 트리 row가 있을 수 있으므로 NOT NULL을 바로 걸 수 없음)
ALTER TABLE trees
    ADD COLUMN workspace_id BIGINT;

-- 2. 워크스페이스가 없는 기존 트리(예: 채팅으로 생성된 트리)는
--    트리마다 전용 워크스페이스를 하나씩 만들어 백필한다 (1:1 매핑이라 공유 불가).
DO $$
DECLARE
    orphan_tree RECORD;
    new_workspace_id BIGINT;
BEGIN
    FOR orphan_tree IN SELECT tree_id FROM trees WHERE workspace_id IS NULL LOOP
        -- 최상위 워크스페이스는 이름이 유니크해야 하므로 tree_id를 붙여 충돌을 피한다.
        INSERT INTO workspaces (name)
        VALUES ('Untitled #' || orphan_tree.tree_id)
        RETURNING workspace_id INTO new_workspace_id;

        UPDATE trees SET workspace_id = new_workspace_id WHERE tree_id = orphan_tree.tree_id;
    END LOOP;
END $$;

-- 3. 백필 완료 후 NOT NULL + FK + UNIQUE 제약 적용
ALTER TABLE trees
    ALTER COLUMN workspace_id SET NOT NULL;

ALTER TABLE trees
    ADD CONSTRAINT fk_trees_workspace
        FOREIGN KEY (workspace_id) REFERENCES workspaces (workspace_id)
        ON DELETE CASCADE;

ALTER TABLE trees
    ADD CONSTRAINT uk_trees_workspace UNIQUE (workspace_id);
