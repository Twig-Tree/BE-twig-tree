-- 폴더에 속하지 않은(최상위) 워크스페이스끼리도 이름 중복 불가
CREATE UNIQUE INDEX uk_workspace_root_name
    ON workspaces (name)
    WHERE folder_id IS NULL;

-- 특정 폴더에 속한 워크스페이스끼리는 이름 중복 불가
CREATE UNIQUE INDEX uk_workspace_folder_name
    ON workspaces (folder_id, name)
    WHERE folder_id IS NOT NULL;

