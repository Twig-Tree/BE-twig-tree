-- 루트(부모 없는) 폴더/워크스페이스의 이름 UNIQUE 제약을 회원 단위로 좁힌다.
-- 기존 인덱스는 전역이라 서로 다른 회원이 같은 이름의 루트 폴더/워크스페이스를 만들 수 없었다.
--
-- 하위 레벨(uk_folder_parent_name, uk_workspace_folder_name)은 folder_parent_id / folder_id 가
-- 이미 특정 회원 소유라 회원 단위로 분리되므로 그대로 둔다.

DROP INDEX uk_folder_root_name;
CREATE UNIQUE INDEX uk_folder_root_name
    ON folders (member_id, name)
    WHERE folder_parent_id IS NULL;

DROP INDEX uk_workspace_root_name;
CREATE UNIQUE INDEX uk_workspace_root_name
    ON workspaces (member_id, name)
    WHERE folder_id IS NULL;
