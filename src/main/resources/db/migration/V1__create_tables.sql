CREATE TABLE IF NOT EXISTS trees (
                      tree_id BIGSERIAL PRIMARY KEY
);

CREATE TABLE IF NOT EXISTS nodes (
                       node_id   BIGSERIAL PRIMARY KEY,

                       name      VARCHAR(255),
                       memo      VARCHAR(255),
                       order_id  BIGINT,

                       parent_id BIGINT,
                       tree_id   BIGINT,

                       CONSTRAINT fk_nodes_parent
                           FOREIGN KEY (parent_id)
                               REFERENCES nodes (node_id)
                               ON DELETE CASCADE,

                       CONSTRAINT fk_nodes_tree
                           FOREIGN KEY (tree_id)
                               REFERENCES trees (tree_id)
                               ON DELETE CASCADE
);

CREATE TABLE IF NOT EXISTS folders (
                         folder_id BIGSERIAL PRIMARY KEY,
                         name VARCHAR(255) NOT NULL,
                         folder_parent_id BIGINT,
                         CONSTRAINT fk_folder_parent
                             FOREIGN KEY (folder_parent_id)
                                 REFERENCES folders (folder_id)
                                 ON DELETE CASCADE
);