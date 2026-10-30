-- 루트 폴더인 경우 (parent가 null)
CREATE UNIQUE INDEX uk_folder_root_name
    ON folders (name)
    WHERE folder_parent_id IS NULL;

-- 부모가 있는 경우
CREATE UNIQUE INDEX uk_folder_parent_name
    ON folders (folder_parent_id, name)
    WHERE folder_parent_id IS NOT NULL;