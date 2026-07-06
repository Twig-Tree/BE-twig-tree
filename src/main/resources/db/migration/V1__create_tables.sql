CREATE TABLE IF NOT EXISTS trees (
                      tree_id BIGSERIAL PRIMARY KEY
);

CREATE TABLE IF NOT EXISTS nodes (
                       node_id   BIGSERIAL PRIMARY KEY,
                       name      VARCHAR(255),
                       memo      VARCHAR(255),
                       order_id  BIGINT,
                       parent_id BIGINT REFERENCES nodes(node_id),
                       tree_id   BIGINT REFERENCES trees(tree_id)
);