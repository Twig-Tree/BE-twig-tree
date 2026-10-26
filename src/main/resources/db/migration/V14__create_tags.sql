CREATE TABLE tags (
    tag_id BIGSERIAL PRIMARY KEY,
    name VARCHAR(30) NOT NULL,
    tree_id BIGINT NOT NULL REFERENCES trees (tree_id) ON DELETE CASCADE,
    created_at timestamptz NOT NULL DEFAULT now(),
    updated_at timestamptz NOT NULL DEFAULT now(),
    CONSTRAINT uk_tag_name UNIQUE (tree_id, name)
);

CREATE TABLE node_tags (
    node_tag_id BIGSERIAL PRIMARY KEY,
    node_id BIGINT NOT NULL REFERENCES nodes (node_id) ON DELETE CASCADE,
    tag_id BIGINT NOT NULL REFERENCES tags (tag_id) ON DELETE CASCADE,
    CONSTRAINT uk_node_tag UNIQUE (node_id, tag_id)
);