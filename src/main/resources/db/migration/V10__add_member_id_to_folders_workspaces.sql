-- 폴더/워크스페이스에 소유자(member)를 필수(NOT NULL)로 연결한다.
--
-- 기존 row 는 소유자 정보를 복원할 수 없으므로 전부 삭제한다.
--  - workspaces 삭제 시 연결된 trees / nodes 는 FK ON DELETE CASCADE 로 함께 삭제된다(V3, V9).
--  - folders 는 self-FK(folder_parent_id) 가 ON DELETE CASCADE 라 순서와 무관하게 삭제된다(V3).

DELETE FROM workspaces;
DELETE FROM folders;

-- folders.member_id
ALTER TABLE folders
    ADD COLUMN member_id BIGINT NOT NULL;

ALTER TABLE folders
    ADD CONSTRAINT fk_folders_member
        FOREIGN KEY (member_id) REFERENCES members (member_id)
        ON DELETE CASCADE;

CREATE INDEX idx_folders_member ON folders (member_id);

-- workspaces.member_id
ALTER TABLE workspaces
    ADD COLUMN member_id BIGINT NOT NULL;

ALTER TABLE workspaces
    ADD CONSTRAINT fk_workspaces_member
        FOREIGN KEY (member_id) REFERENCES members (member_id)
        ON DELETE CASCADE;

CREATE INDEX idx_workspaces_member ON workspaces (member_id);
