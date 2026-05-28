-- 루트 노드 (parent_id IS NULL)
CREATE UNIQUE INDEX uk_nodes_root_order_id
    ON nodes (order_id)
    WHERE parent_id IS NULL;

-- 일반 노드 (parent_id IS NOT NULL)
CREATE UNIQUE INDEX uk_nodes_parent_order_id
    ON nodes (parent_id, order_id)
    WHERE parent_id IS NOT NULL;