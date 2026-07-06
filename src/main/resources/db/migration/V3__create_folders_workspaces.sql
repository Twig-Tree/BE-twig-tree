-- nodes 테이블 FK를 명시적 이름 + CASCADE로 교체
ALTER TABLE nodes DROP CONSTRAINT nodes_parent_id_fkey;
ALTER TABLE nodes ADD CONSTRAINT fk_nodes_parent
    FOREIGN KEY (parent_id) REFERENCES nodes (node_id) ON DELETE CASCADE;

ALTER TABLE nodes DROP CONSTRAINT nodes_tree_id_fkey;
ALTER TABLE nodes ADD CONSTRAINT fk_nodes_tree
    FOREIGN KEY (tree_id) REFERENCES trees (tree_id) ON DELETE CASCADE;

-- folders 테이블 신규 생성
CREATE TABLE IF NOT EXISTS folders (
                                       folder_id BIGSERIAL PRIMARY KEY,
                                       name VARCHAR(255) NOT NULL,
    folder_parent_id BIGINT,
    CONSTRAINT fk_folder_parent
    FOREIGN KEY (folder_parent_id)
    REFERENCES folders (folder_id)
    ON DELETE CASCADE
    );

-- workspaces 테이블 신규 생성
CREATE TABLE IF NOT EXISTS workspaces (
                                          workspace_id BIGSERIAL PRIMARY KEY,
                                          name VARCHAR(255) NOT NULL,
    folder_id BIGINT,
    CONSTRAINT fk_workspace_folder
    FOREIGN KEY (folder_id)
    REFERENCES folders(folder_id)
    ON DELETE CASCADE
    );