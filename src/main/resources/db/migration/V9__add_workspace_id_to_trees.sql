-- 트리-워크스페이스 1:1 매핑을 위한 FK 컬럼 추가
-- 워크스페이스가 삭제되면 연결된 트리도 함께 삭제됩니다 (ON DELETE CASCADE).
ALTER TABLE trees
    ADD COLUMN workspace_id BIGINT NOT NULL;

ALTER TABLE trees
    ADD CONSTRAINT fk_trees_workspace
        FOREIGN KEY (workspace_id) REFERENCES workspaces (workspace_id)
        ON DELETE CASCADE;

ALTER TABLE trees
    ADD CONSTRAINT uk_trees_workspace UNIQUE (workspace_id);
